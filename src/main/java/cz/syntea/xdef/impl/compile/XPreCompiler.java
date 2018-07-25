/*
 * Copyright 2018 Syntea software group a.s. All rights reserved.
 *
 * File: XPreCompiler.java, created 2018-07-21.
 *
 * This file may be used, copied, modified and distributed only in accordance
 * with the terms of the limited licence contained in the accompanying
 * file LICENSE.TXT.
 */
package cz.syntea.xdef.impl.compile;

import cz.syntea.xdef.XDConstants;
import cz.syntea.xdef.XDPool;
import cz.syntea.xdef.impl.XDefinition;
import cz.syntea.xdef.impl.ext.XExtUtils;
import cz.syntea.xdef.msg.SYS;
import cz.syntea.xdef.msg.XDEF;
import cz.syntea.xdef.msg.XML;
import cz.syntea.xdef.sys.ArrayReporter;
import cz.syntea.xdef.sys.Report;
import cz.syntea.xdef.sys.ReportWriter;
import cz.syntea.xdef.sys.SBuffer;
import cz.syntea.xdef.sys.SPosition;
import cz.syntea.xdef.sys.SRuntimeException;
import cz.syntea.xdef.sys.SThrowable;
import cz.syntea.xdef.sys.SUtils;
import cz.syntea.xdef.sys.StringParser;
import cz.syntea.xdef.xml.KParsedAttr;
import cz.syntea.xdef.xml.KParsedElement;
import cz.syntea.xdef.xml.KXmlConstants;
import cz.syntea.xdef.xml.KXmlUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/** Reads source X-definitions and prepares list of PNodes with X-definitions.
 * @author Trojan
 */
public class XPreCompiler extends XDefReader implements PPreCompiler {
	/** index of NameSpace of X-definitions. */
	static final int NS_XDEF_INDEX = 0;
	/** index of NameSpace of XML. */
	static final int NS_XML_INDEX = NS_XDEF_INDEX + 1; //1
	/** index of NameSpace of XML NameSpace. */
	static final int NS_XMLNS_INDEX = NS_XML_INDEX + 1; //2
	/** index of NameSpace of XLink. */
	static final int NS_XLINK_INDEX = NS_XMLNS_INDEX + 1; //3
	/** index of NameSpace of XInclude. */
	static final int NS_XINCLUDE_INDEX = NS_XLINK_INDEX + 1; //4
	/** index of NameSpace of XML Schema. */
	static final int NS_XMLSCHEMA_INDEX = NS_XINCLUDE_INDEX + 1; //5
	/** Table of NameSpace prefixes. */
	static final Map<String, Integer> PREDEFINED_PREFIXES =
		new TreeMap<String, Integer>();

	/** PNodes with parsed source items. */
	private final ArrayList<PNode> _xdefPNodes = new ArrayList<PNode>();
	/** Source files table - to prevent to doParse the source twice. */
	private final ArrayList<Object> _sources = new ArrayList<Object>();
	/** Array of thesaurus sources item. */
	private final ArrayList<PNode> _thesaurus = new ArrayList<PNode>();
	/** Array of BNF sources. */
	private final ArrayList<PNode> _listBNF = new ArrayList<PNode>();
	/** Array of declaration source items. */
	private final ArrayList<PNode> _listDecl = new ArrayList<PNode>();
	/** Array of collection source items. */
	private final ArrayList<PNode> _listCollection = new ArrayList<PNode>();
	/** Array of component sources. */
	private final ArrayList<PNode> _listComponent = new ArrayList<PNode>();
	/** Code generator. */
	private final CompileCode _codeGenerator;

	/** Actual node */
	private PNode _actPNode;
	/** Display mode */
	private final byte _displayMode;
	/** includes. */
	private Element _includeElement;
	/** The nesting level of XML node. */
	private int _level;
	/** The nesting level of XML node. */
	private boolean _macrosProcessed;
	/** Include list of URL's. */
	private final ArrayList<Object> _includeList = new ArrayList<Object>();
	/** List of macro definitions. */
	private final Map<String, XScriptMacro> _macros =
		new TreeMap<String, XScriptMacro>();

	/** Creates a new instance of XDefCompiler
	 * @param reporter The reporter.
	 * @param extClasses The array with external classes declared by user.
	 * @param displayMode display mode: 0 .. false, 1 .. true, 2 .. errors
	 * @param debugMode debug mode flag.
	 * @param ignoreUnresolvedExternals ignore unresolved externals flag.
	 */
	public XPreCompiler(final ReportWriter reporter,
		final Class<?>[] extClasses,
		final byte displayMode,
		final boolean debugMode,
		final boolean ignoreUnresolvedExternals) {
		super(reporter);
		_displayMode = displayMode;
		 //"xml"
		PREDEFINED_PREFIXES.put(XMLConstants.XML_NS_PREFIX, NS_XML_INDEX);
		//"xmlns",
		PREDEFINED_PREFIXES.put(XMLConstants.XMLNS_ATTRIBUTE, NS_XMLNS_INDEX);
		_codeGenerator = new CompileCode(extClasses,
			2, debugMode, ignoreUnresolvedExternals);
		_codeGenerator._namespaceURIs.add("."); //dummy namespace
		_codeGenerator._namespaceURIs.add(XMLConstants.XML_NS_URI);
		_codeGenerator._namespaceURIs.add(XMLConstants.XMLNS_ATTRIBUTE_NS_URI);
		_codeGenerator._namespaceURIs.add(KXmlConstants.XLINK_NS_URI);
		_codeGenerator._namespaceURIs.add(KXmlConstants.XINCLUDE_NS_URI);
		_codeGenerator._namespaceURIs.add(//schema
			XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI);
		_macrosProcessed = false;
	}

	/** Report not legal attributes. All allowed attributes should be
	 * processed and removed. Not legal attributes generates an error message.
	 * @param pnode node to be checked.
	 */
	final void reportNotAllowedAttrs(final PNode pnode) {
		for (PAttr attr: pnode._attrs) {
			 //Attribute '&{0}' not allowed here
			error(attr._value, XDEF.XDEF254, attr._name);
		}
		pnode._attrs.clear();
	}

	@Override
	/** This method is called after all attributes of the current element
	 * attribute list was reached. The implementation may check the list of
	 * attributes and to invoke appropriate actions. The method is invoked
	 * when parser reaches the end of the attribute list.
	 * @param parsedElem contains name of the element, name space URI and
	 * the list of attributes.
	 */
	public void elementStart(final KParsedElement parsedElem) {
		String qName = parsedElem.getParsedName();
		if (_includeElement == null) {
			if (_actPNode != null && _actPNode._value != null &&
				_actPNode._value.getString().length() > 0) {
				processText();
			}
			_actPNode = new PNode(qName,
				parsedElem.getParsedNameSourcePosition(),
				_actPNode,
				_actPNode==null? (byte) 0 : _actPNode._xdVersion,
				"1.1".equals(getXmlVersion()) ? (byte) 11 : (byte) 10);
		}
		String elemPrefix;
		String elemLocalName;
		int ndx;
		if ((ndx = qName.indexOf(':')) >= 0) {
			elemPrefix = qName.substring(0, ndx);
			_actPNode._localName = elemLocalName = qName.substring(ndx + 1);
		} else {
			elemPrefix = "";
			_actPNode._localName = elemLocalName = qName;
		}
		_actPNode._nsURI = parsedElem.getParsedNSURI();
		if (_level == -1) {
			_codeGenerator._namespaceURIs.remove(0);
			String uri = parsedElem.getParsedNSURI();
			if ("def".equals(elemLocalName)
				|| "thesaurus".equals(elemLocalName)
				|| "declaration".equals(elemLocalName)
				|| "BNFGrammar".equals(elemLocalName)
				|| "collection".equals(elemLocalName))  {
				String projectNS; // = XDConstants.XDEF20_NS_URI;
				KParsedAttr ka;
				byte ver;
				if ((ka = parsedElem.getAttrNS(
					KXmlConstants.XDEF20_NS_URI, "metaNamespace")) != null
					|| (ka = parsedElem.getAttrNS(
						KXmlConstants.XDEF31_NS_URI, "metaNamespace")) != null){
					projectNS = ka.getValue().trim();
					ver=KXmlConstants.XDEF31_NS_URI.equals(ka.getNamespaceURI())
						 ? XDConstants.XD31_ID : XDConstants.XD20_ID;
					if (XExtUtils.uri(projectNS).errors()) {
						//Attribute 'metaNamespace' must contain a valid URI
						error(ka.getPosition(), XDEF.XDEF253);
					}
					parsedElem.remove(ka);
				} else {
					if (KXmlConstants.XDEF20_NS_URI.equals(uri)
						|| KXmlConstants.XDEF31_NS_URI.equals(uri)) {
						ver = KXmlConstants.XDEF31_NS_URI.equals(uri)
							? XDConstants.XD31_ID : XDConstants.XD20_ID;
						projectNS = uri;
					} else {
						//Namespace of X-definitions is required
						error(_actPNode._name, XDEF.XDEF256);
						projectNS = KXmlConstants.XDEF31_NS_URI;
						ver = XDConstants.XD31_ID;
					}
				}
				_actPNode._xdVersion = ver;
				_codeGenerator._namespaceURIs.add(0, projectNS);
			} else {
				_codeGenerator._namespaceURIs.add(0, uri);
				//X-definition or X-collection expected
				error(_actPNode._name, XDEF.XDEF255);
			}
		}
		for (int i = 0, max = parsedElem.getLength(); i < max; i++) {
			KParsedAttr ka = parsedElem.getAttr(i);
			String key = ka.getName();
			String value = ka.getValue();
			if (key.startsWith("xmlns")) { //addAttr namespace URI to the list.
				int nsndx = _codeGenerator._namespaceURIs.indexOf(value.trim());
				if (nsndx < 0) {
					nsndx = _codeGenerator._namespaceURIs.size();
					_codeGenerator._namespaceURIs.add(value.trim());
				}
				if (key.length() == 5) { //default prefix
					_actPNode._nsPrefixes.put("", nsndx);
				} else if (key.charAt(5) == ':') { //prefix name
					_actPNode._nsPrefixes.put(key.substring(6), nsndx);
				}
			} else if ("collection".equals(elemLocalName) &&
				key.startsWith("impl-")) {//continue; ignore, just documentation
			} else {
				PAttr item = new PAttr(key,
					new SBuffer(value, ka.getPosition()), null, -1);
				if ((ndx = key.indexOf(':')) >= 0) {
					String prefix = key.substring(0, ndx);
					item._localName = key.substring(ndx + 1);
					Integer nsndx = _actPNode._nsPrefixes.get(prefix);
					if (nsndx == null) {
						String u;
						if ((u = ka.getNamespaceURI()) != null) {
							int x = _codeGenerator._namespaceURIs.indexOf(u);
							if (x < 0) {
								nsndx = _codeGenerator._namespaceURIs.size();
								_codeGenerator._namespaceURIs.add(u);
							} else {
								nsndx = x;
							}
							_actPNode._nsPrefixes.put(prefix, nsndx);
						}
					}
					if (nsndx != null) {
						item._nsURI = _codeGenerator._namespaceURIs.get(nsndx);
						if ((item._nsindex=nsndx) == NS_XDEF_INDEX &&
							"script".equals(item._localName)) {
							StringParser p = new StringParser(
								new SBuffer(value, ka.getPosition()));
							p.skipSpaces();
							if (p.isToken("template")) {
								p.skipSpaces();
								if (!p.eos() && !p.isChar(';')) {
									error(p, XDEF.XDEF425);//Script error
								}
								_actPNode._template = true;
							}
						}
					} else {
						item._nsindex = -1;
					}
				} else {
					item._localName = key;
					item._nsindex = -1;
				}
				_actPNode._attrs.add(item);
			}
		}
		Integer nsuriIndex = _actPNode._nsPrefixes.get(elemPrefix);
		if (nsuriIndex != null) {
			int urindx;
			if (((urindx = nsuriIndex) == NS_XINCLUDE_INDEX)) {
				String nsuri = _codeGenerator._namespaceURIs.get(urindx);
				Element el;
				if (_includeElement == null) {
					el = _includeElement = KXmlUtils.newDocument(nsuri,
						_actPNode._name.getString(), null).getDocumentElement();
				} else {
					el = _includeElement.getOwnerDocument().createElementNS(
						nsuri, _actPNode._name.getString());
					_includeElement.appendChild(el);
				}
				for (PAttr aval: _actPNode._attrs) {
					if (aval._nsindex < 0) {
						el.setAttribute(aval._name, aval._value.getString());
					} else {
						el.setAttributeNS(
							_codeGenerator._namespaceURIs.get(aval._nsindex),
							aval._name,
							aval._value.getString());
					}
				}
				return;
			} else {
				_actPNode._nsindex = urindx;
			}
		}
		if (_level == -1) {
			if ("collection".equals(elemLocalName)) {
				processIncludeList();
				reportNotAllowedAttrs(_actPNode);
				_listCollection.add(_actPNode);
			} else if ("BNFGrammar".equals(elemLocalName)) {
				_level++;
				 _listBNF.add(_actPNode);
			} else if ("thesaurus".equals(elemLocalName)) {
				_level++;
				_thesaurus.add(_actPNode);
			} else if ("declaration".equals(elemLocalName)) {
				_level++;
				_listDecl.add(0, _actPNode);
			} else if ("component".equals(elemLocalName)) {
				_level++;
				_listComponent.add(0, _actPNode);
			} else {
				if (!"def".equals(elemLocalName)) {
					error(_actPNode._name, XDEF.XDEF259);//X-definition expected
				}
				_level++;
				String defName =
					_actPNode.getNameAttr(false, true, getReportWriter());
				if (defName == null) {
					defName = "";
				}
				// Because there is not yet connected an X-definition to
				// the PNode we create a dumy one in fact just to store
				// the X-definition name (we nead it to be able to compile
				// internal declarations, BNGGrammars, components and
				// thesaurus items).
				_actPNode._xdef = new XDefinition(defName,
					null, null, null, _actPNode._xmlVersion);
				processIncludeList();
				// check duplicate of X-definition
				for (PNode p: _xdefPNodes) {
					if (defName.equals(p._xdef.getName())) {
						if (defName.length() == 0) {
							//Only one X-definition in the compiled XDPool
							// may be without name
							error(_actPNode._name, XDEF.XDEF212);
						} else {
							//X-definition '&{0}' already exists
							error(_actPNode._name, XDEF.XDEF303, defName);
						}
						defName = null;
//						String s = null;
//						for (int count = 1; s == null; count++) {
//							s = defName + "_DUPLICATED_NAME_" + count;
//							for (PNode q: _xdefPNodes) {
//								if (s.equals(q. _xdef.getName())) {
//									s = null;
//									break;
//								}
//							}
//						}
//						defName = s;
					}
				}
				if (defName != null) {
//					_xdefNames.add(defName);
					_xdefPNodes.add(_actPNode);
				}
			}
		} else {
			_level++;
			_actPNode._parent._childNodes.add(_actPNode);
		}
//		if (_level == 1 && "declaration".equals(elemLocalName)) {
//			_listDecl.add(0, _actPNode);
//		}

	}

	@Override
	/** This method is invoked when parser reaches the end of element. */
	public void elementEnd() {
		if (_includeElement != null) {
			String ns = _includeElement.getPrefix();
			ns = ns == null || ns.length() == 0 ? "xmlns" : "xmlns:" + ns;
			NamedNodeMap nm = _includeElement.getAttributes();
			if (nm.getLength() > 0) { //other attributes
				for (int i = 0; i < nm.getLength(); i++) {
					Node n = nm.item(i);
					String name = n.getNodeName();
					if (!"href".equals(name) && !"parse".equals(name)
						&& !ns.equals(name)) {
						//Xinclude - unknown attribute &{0}
						error(_actPNode._name, XML.XML305, n.getNodeName());
					}
				}
			}
			_includeElement = null;
			_actPNode = _actPNode._parent;
			return;
		}
		if (_actPNode._value != null &&
			_actPNode._value.getString().length() > 0) {
			processText();
		}
		_level--;
		_actPNode = _actPNode._parent;
	}

	@Override
	/** New text value of current element parsed.
	 * @param text SBuffer with value of text node.
	 */
	public final void text(final SBuffer text) {
		if (_includeElement != null) {
			return;
		}
		if (_actPNode._template && _level > 0 &&
			_actPNode._nsindex != NS_XDEF_INDEX) {
			SBuffer sval = null;
			if (text != null) {
				sval = new SBuffer(text.getString(), text);
			}
			if (_actPNode._value == null) {
				_actPNode._value = sval;
			} else {
				_actPNode._value.appendToBuffer(sval);
			}
			return;
		}
		if (text == null) {
			return; // no string
		}
		String s = text.getString();
		int len = s.length() - 1;
		while (len >= 0 && s.charAt(len) <= ' ') {
			len--;
		}
		if (len < 0) {
			return; // empty string
		}
		SBuffer sb =new SBuffer(s, text);
		if (_actPNode._value == null) {
			_actPNode._value = sb;
		} else {
			if (_actPNode._value == null) {
				_actPNode._value = sb;
			} else {
				_actPNode._value.appendToBuffer(sb);
			}
		}
		if (_actPNode._nsindex == NS_XDEF_INDEX &&
			"def".equals(_actPNode._localName)) {
			XScriptParser xparser = new XScriptParser(_actPNode._xmlVersion);
			xparser.setLineInfoFlag(true);
			xparser.setReportWriter(getReportWriter());
			xparser.setSourceBuffer(_actPNode._value);
			// it still may be a comment
			if (xparser.nextSymbol() != XScriptParser.NOCHAR) {
				//Text value is not allowed here
				lightError(_actPNode._value, XDEF.XDEF260);
			}
			_actPNode._value = null;//prevent repeated message, remove this text
		}
	}

	/** Generate text node */
	private void genTextNode() {
		String name = "";
		for (String prefix : _actPNode._nsPrefixes.keySet()) {
			if (_actPNode._nsPrefixes.get(prefix) == NS_XDEF_INDEX) {
				name = prefix + ":text";
				break;
			}
		}
		PNode p = new PNode(name,
			new SPosition(_actPNode._value),
			_actPNode,
			_actPNode._xdVersion,
			_actPNode._xmlVersion);
		p._nsURI = _codeGenerator._namespaceURIs.get(NS_XDEF_INDEX);
		p._nsindex = NS_XDEF_INDEX;
		p._localName = "text";
		p._value = _actPNode._value;
		_actPNode._value = null;
		_level++;
		_actPNode._childNodes.add(p);
		_level--;
	}

	private void processText() {
		if (_actPNode._template && _level > 0 &&
			_actPNode._nsindex != NS_XDEF_INDEX) {
			genTextNode();
			return;
		}
		XScriptParser xparser = new XScriptParser(_actPNode._xmlVersion);
		xparser.setLineInfoFlag(true);
		xparser.setReportWriter(getReportWriter());
		xparser.setSourceBuffer(_actPNode._value);
		if (xparser.nextSymbol() == XScriptParser.NOCHAR) {
			_actPNode._value = null; // remove this text
			return;
		}
		if (_actPNode._nsindex == NS_XDEF_INDEX) {
			if ("text".equals(_actPNode._localName) ||
				"BNFGrammar".equals(_actPNode._localName) ||
				"thesaurus".equals(_actPNode._localName) ||
				"declaration".equals(_actPNode._localName) ||
				"component".equals(_actPNode._localName) ||
				"macro".equals(_actPNode._localName)) {
				return; //text is processed in the pnode
			} else if (!"mixed".equals(_actPNode._localName) &&
				!"choice".equals(_actPNode._localName) &&
				!"list".equals(_actPNode._localName) &&
//				!"PI".equals(_actPNode._localName) && //TODO
//				!"comment".equals(_actPNode._localName) && //TODO
//				!"document".equals(_actPNode._localName) && //TODO
//				!"value".equals(_actPNode._localName) && //TODO
//				!"attlist".equals(_actPNode._localName) && //TODO
				!"sequence".equals(_actPNode._localName) &&
				!"any".equals(_actPNode._localName)) {
				//Text value is not allowed here
				lightError(_actPNode._value, XDEF.XDEF260);
				_actPNode._value = null; //prevent repeated message
				return;
			}
		}
		if (_level == 0) {
			//Text value not allowed here
			lightError(_actPNode._value, XDEF.XDEF260);
			_actPNode._value = null; //prevent repeated message
		} else {
			genTextNode(); //generate text node
		}
	}

	/** Parse string and addAttr it to the set of definitions.
	 * @param source The source string with definitions.
	 */
	public final void parseString(final String source) {
		if (_sources.indexOf(source) >= 0 || source.length() == 0) {
			return;  //we ignore already declared or empty strings
		}
		if (source.charAt(0) == '<') {
			parseString(source, "STRING");
		} else {
			try {
				URL u = new URL(source);
				parseURL(u);
			} catch (Exception ex) {
				parseFile(new File(source));
			}
		}
	}

	/** Parse string and addAttr it to the set of X-definitions.
	 * @param source source string with X-definitions.
	 * @param srcName pathname of source (URL or an identifying name or null).
	 */
	public final void parseString(final String source, final String srcName) {
		if (_sources.indexOf(source) >= 0 || source.length() == 0) {
			return;  //we ignore already declared or empty strings
		}
		char c;
		if ((c = source.charAt(0)) <= ' ' || c == '<') {
			_sources.add(source);
			try {
				parseStream(new ByteArrayInputStream(source.getBytes("UTF-8")),
					srcName);
			} catch (RuntimeException ex) {
				throw ex;
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		} else {
			parseFile(source);
		}
	}

	/** Parse file with source X-definition and addAttr it to the set
	 * of definitions.
	 * @param fileName pathname of file with with X-definitions.
	 */
	public final void parseFile(final String fileName) {
		parseFile(new File(fileName));
	}

	/** Parse file with source X-definition and addAttr it to the set
	 * of definitions.
	 * @param file The file with with X-definitions.
	 */
	public final void parseFile(final File file) {
		try {
			URL url = file.toURI().toURL();
			for (Object o: _sources) {
				if (o instanceof URL && url.equals(o)) {
					return; //found in list
				}
			}
			_sources.add(url);
			parseStream(new FileInputStream(file), url.toExternalForm());
		} catch (RuntimeException ex) {
			throw ex;
		} catch (IOException ex) {
			//Can't read X-definition from the file &{0}
			throw new SRuntimeException(XDEF.XDEF902,
				(file == null ? (String) null : file.getAbsolutePath()));
		}
	}

	/** Parse InputStream source X-definition and addAttr it to the set
	 * of definitions.
	 * @param in input stream with the X-definition.
	 * @param srcName name of source data used in reporting (SysId) or
	 * <tt>null</tt>.
	 */
	public final void parseStream(final InputStream in, final String srcName) {
		if (_sources.contains(in)) {
			return;
		}
		_sources.add(in);
		_sysId = srcName;
		_level = -1;
		_actPNode = null;
		try {
			doParse(in, srcName);
		} catch (RuntimeException ex) {
			throw ex;
		} catch (Exception ex) {
			_actPNode = null; //just let gc to do the job
			if (_displayMode > XDPool.DISPLAY_FALSE) {
				if (!(ex instanceof SThrowable)) {
					getReportWriter().error(SYS.SYS066, //Internal error&{0}{: }
						"when parsing document\n" + ex);
				}
			} else {
				if (ex instanceof SThrowable &&
					"SYS012".equals(((SThrowable) ex).getMsgID())) {
					throw (SRuntimeException) ex; //Errors detected&{0}{: }
				} else {
					//Internal error: &{0}
					throw new SRuntimeException(SYS.SYS066,
						ex, "when parsing document\n" + ex);
				}
			}
		}
		_actPNode = null; //just let gc to do the job
	}

	/** Parse data with source X-definition given by URL and addAttr it
	 * to the set of X-definitions.
	 * @param url URL of the file with the X-definition.
	 */
	public final void parseURL(final URL url) {
		if (url == null) {
			//Can't read X-definition from the file &{0}
			getReportWriter().error(XDEF.XDEF902, "null");
			return;
		}
		for (Object o: _sources) {
			if (o instanceof URL && url.equals((URL) o)) {
				return; //prevents to doParse the source twice.
			}
		}
		String srcName = url.toExternalForm();
		_sources.add(srcName);
		try {
			parseStream(url.openStream(), srcName);
		} catch (RuntimeException ex) {
			throw ex;
		} catch (Exception ex) {
			//Can't read X-definition from the file &{0}
			throw new SRuntimeException(XDEF.XDEF902, srcName);
		}
	}

	/** Process include list from header of X-definition. */
	private void processIncludeList() {
		/** let's check some attributes of X-definition.*/
		SBuffer include = _actPNode.getXdefAttr(
			"include", false, true, getReportWriter());
		processIncludeList(include, _includeList,
			_actPNode._name.getSysId(), getReportWriter());
	}

	/** Process list of file specifications and/or URLs. Result of list is added
	 * to the includeList (if the includeList already contains an item the
	 * item is skipped. If the argument reporter is not <tt>null</tt> and an
	 * error occurs then the error is written to reporter. If reporter is
	 * <tt>null</tt> then an SRuntimeException is thrown.
	 * @param include SBuffer with list of items, separator is ",". Wildcard
	 * characters are permitted.
	 * @param includeArray ArrayList with items.
	 * @param sysId actual path.
	 * @param reporter report writer or <tt>null</tt>.
	 * @throws SRuntimeException if list contains error and reporter is null.
	 */
	private static void processIncludeList(final SBuffer include,
		final ArrayList<Object> includeArray,
		final String sysId,
		final ReportWriter reporter) {
		if (include == null) {
			return;
		}
		ReportWriter myreporter =
			reporter == null ? new ArrayReporter() : reporter;
		StringTokenizer st =
			new StringTokenizer(include.getString(), " \t\n\r\f,");
		while (st.hasMoreTokens()) {
			String s = st.nextToken();
			if (s.startsWith("https:") || s.startsWith("http:") ||
				s.startsWith("ftp:") || s.startsWith("file:")) {
				try {
					URL u = new URL(URLDecoder.decode(s, "UTF-8"));
					if (includeArray.contains(u)) {
						continue;
					}
					includeArray.add(u);
				} catch (Exception ex) {
					myreporter.error(SYS.SYS024, s); //File doesn't exist: &{0}
				}
			} else {
				if (s.indexOf(':') < 0 &&
					!s.startsWith("/") && !s.startsWith("\\")) {//no path
					if (sysId != null) {//take path from sysId
						try {
							URL u = new URL(URLDecoder.decode(sysId, "UTF-8"));
							if (!"file".equals(u.getProtocol())) {
								String v =u.toExternalForm().replace('\\', '/');
								int i = v.lastIndexOf('/');
								if (i >= 0) {
									v = v.substring(0, i + 1);
								}
								u = new URL(URLDecoder.decode(v + s, "UTF-8"));
								if (includeArray.contains(u)) {
									continue;
								}
								includeArray.add(u);
								continue;
							} else {
								String p = new File(u.getFile()).
									getCanonicalPath().replace('\\', '/');
								int i = p.lastIndexOf('/');
								s = i>0 ? p.substring(0, i + 1) + s : ('/' + s);
							}
						} catch (Exception ex) {
							s = ""; // no file
						}
					}
				}
				File[] list = SUtils.getFileGroup(s);
				if (list.length == 0) {
					myreporter.error(SYS.SYS024, s); //File doesn't exist: &{0}
				} else {
					for (File f: list) {
						try {
							if (f.canRead()) {
								if (includeArray.contains(
									f.getCanonicalPath())) {
									continue; //file already exists
								}
								includeArray.add(f);
								continue;
							}
						} catch (IOException ex) {}
						//File doesn't exist: &{0}
						myreporter.error(SYS.SYS024, s);
					}
				}
			}
		}
		if (reporter == null && myreporter.errors()) {
			myreporter.checkAndThrowErrors();
		}
	}


	/** Check if the name of X-definition is OK.
	 * @param name name of X-definition
	 * @return true if the name of X-definition is OK.
	 */
	final static boolean chkDefName(final String name, byte xmlVersion) {
		if (name.length() == 0) {
			return true; //nameless is also name
		}
		if (StringParser.getXmlCharType(name.charAt(0),  xmlVersion) !=
			StringParser.XML_CHAR_NAME_START) {
			return false;
		}
		char c;
		boolean wasColon = false;
		for (int i = 1; i < name.length(); i++) {
			if (StringParser.getXmlCharType(c = name.charAt(i),  xmlVersion) !=
				StringParser.XML_CHAR_NAME_START && (c  < '0' && c > '9')) {
				if (!wasColon && c == ':') { // we allow one colon inside name
					wasColon = true;
					if (i + 1 < name.length()
						&& StringParser.getXmlCharType(
							name.charAt(++i), xmlVersion)
						!= StringParser.XML_CHAR_NAME_START){//must follow name
						continue;
					}
				}
				return false;
			}
		}
		return true;
	}

	/** Check if the node has no nested child nodes. */
	final void chkNestedElements(final PNode pnode) {
		for (PNode p: pnode._childNodes) {
			//Nested child elements are not allowed here
			error(p._name, XDEF.XDEF219);
		}
	}

	/** Prepare list of declared macros and expand macro references. */
	public final void prepareMacros() {
		if (_macrosProcessed) {
			return;
		}
		// doParse of definitions from include list
		for (int i = 0; i < _includeList.size(); i++) {
			Object o = _includeList.get(i);
			if (o instanceof URL) {
				parseURL((URL) o);
			} else {
				parseFile((File) o);
			}
		}
		for (PNode xd: _xdefPNodes) {
			String defName = xd._xdef.getName();
			List<PNode> macros = xd.getXDefChildNodes("macro");
			for (PNode macro : macros) {
				Map<String, String> params = new TreeMap<String, String>();
				chkNestedElements(macro);
				for (PAttr val : macro._attrs) {
					params.put(val._name, val._value.getString());
				}
				XScriptMacro m = new XScriptMacro(
					macro.getNameAttr(true, true, getReportWriter()),
					defName,
					params,
					macro._value,
					getReportWriter());
				if (_macros.containsKey(m.getName())) {
					//Macro '&{0}' redefinition
					Report rep = Report.error(XDEF.XDEF482, m.getName());
					macro._name.putReport(rep, getReportWriter());
				} else {
					_macros.put(m.getName(), m);
				}
			}
//			def.removeChildNodes(macros); // remove all items with macros
		}
		// expand macros
		ReportWriter reporter = getReportWriter();
		for (PNode p: _xdefPNodes) {
			p.expandMacros(reporter, p._xdef.getName(), _macros);
		}
		for (PNode p: _thesaurus) {
			p.expandMacros(reporter, null, _macros);
		}
		for (PNode p: _listBNF) {
			p.expandMacros(reporter, null, _macros);
		}
		for (PNode p: _listDecl) {
			p.expandMacros(reporter, null, _macros);
		}
		for (PNode p: _listComponent) {
			p.expandMacros(reporter, null, _macros);
		}
		_macrosProcessed = true;
	}

	@Override
	/** Get code generator.
	 * @return the code generator.
	 */
	public CompileCode getCodeGenerator() {return _codeGenerator;}

	@Override
	/** Get sources of X-defintions.
	 * @return array with sources of X-defintions.
	 */
	public List<Object> getSources() {return _sources;}

	@Override
	/** Get precompiled sources (PNodes) of X-definition items.
	 * @return array with PNodes with X-definitions.
	 */
	public List<PNode> getPXDefs() {return _xdefPNodes;}

	@Override
	/** Get precompiled sources (PNodes) of Thesaurus items.
	 * @return array with PNodes.
	 */
	public final List<PNode> getPThesaurusList() {return _thesaurus;}

	@Override
	/** Get precompiled sources (PNodes) of collection items.
	 * @return array with PNodes.
	 */
	public final List<PNode> getPCollections() {return _listCollection;}

	@Override
	/** Get precompiled sources (PNodes) of declaration items.
	 * @return array with PNodes.
	 */
	public final List<PNode> getPDeclarations() {return _listDecl;}

	@Override
	/** Get precompiled sources (PNodes) of components items.
	 * @return array with PNodes.
	 */
	public final List<PNode> getPComponents() {return _listComponent;}

	@Override
	/** Get precompiled sources (PNodes) of BNF Grammar items.
	 * @return array with PNodes.
	 */
	public final List<PNode> getPBNFs() {return _listBNF;}

}