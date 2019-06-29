package org.xdef.json;

import org.xdef.XDConstants;
import org.xdef.msg.JSON;
import org.xdef.msg.XDEF;
import org.xdef.sys.SRuntimeException;
import org.xdef.sys.StringParser;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import static org.xdef.sys.JSONUtil.J_ARRAY;
import static org.xdef.sys.JSONUtil.J_BOOLEAN;
import static org.xdef.sys.JSONUtil.J_ITEM;
import static org.xdef.sys.JSONUtil.J_MAP;
import static org.xdef.sys.JSONUtil.J_NULL;
import static org.xdef.sys.JSONUtil.J_NUMBER;
import static org.xdef.sys.JSONUtil.J_STRING;

/** Conversion of XML to JSON (both versions - W3C and XDEF)
 * @author Vaclav Trojan
 */
public class XmlToJson extends JsonToXml {
	/** Document used to create X-definition. */
	private boolean _isW3C;

	/** Prepare instance of JX. */
	private XmlToJson() {super();}

	/** Create JSON name from XML name.
	 * @param name XML name.
	 * @return JSON name.
	 */
	private static String toJsonName(final String name) {
		if ("_".equals(name)) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			char ch = name.charAt(i);
			if (ch == '_' && i + 2 < name.length()) {
				if (isJChar(name, i)) {
					int ndx = name.indexOf('_', i+1);
					int x = Integer.parseInt(name.substring(i+2, ndx), 16);
					sb.append((char) x);
					i = ndx;
				} else {
					sb.append('_');
				}
			} else {
				sb.append(ch);
			}
		}
		return sb.toString();
	}

	/** Create JSON Map with attributes from element.
	 * @param e element with attributes.
	 * @return JSON Map with attributes.
	 */
	private Map<String, Object> genAttrs(final Element e) {
		if (e.hasAttributes()) {
			Map<String,Object> attrs = new LinkedHashMap<String,Object>();
			NamedNodeMap nnm = e.getAttributes();
			for (int i = nnm.getLength() - 1; i >= 0; i--) {
				Node n = nnm.item(i);
				if (!_isW3C && !_jsNamespace.equals(n.getNamespaceURI())) {
					attrs.put(toJsonName(n.getNodeName()),
						getJValue(n.getNodeValue()));
				}
			}
			return attrs;
		}
		return null;
	}

	/** Get JSON value from string.
	 * @param source string with JSON simple value
	 * @return object with JSOM value
	 */
	private static Object getJValue(final String source) {
		String s;
		if (source == null || "null".equals(s = source.trim())) {
			return null;
		}
		if (s.isEmpty()) {
			return "";
		}
		if ("true".equals(s)) {
			return Boolean.TRUE;
		} else if ("false".equals(s)) {
			return Boolean.FALSE;
		}
		switch (s.charAt(0)) {
			case '-':
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				try {
					if (s.indexOf('.') > 0
						|| s.indexOf('e') > 0 || s.indexOf('E') > 0) {
						return new BigDecimal(s);
					} else {
						try {
							return new Long(s);
						} catch (Exception ex) {
							return new BigInteger(s);
						}
					}
				} catch (Exception ex) {
					return s;
				}
			case '"': // JSON string
				if (s.length() > 1 && s.charAt(s.length() - 1) == '"'
					&& s.charAt(s.length() - 2) != '\\') {
					s = s.substring(1, s.length() - 1);
					StringBuilder sb = new StringBuilder();
					for (int i = 0;  i < s.length(); i++) {
						char ch = s.charAt(i);
						if (ch == '\\') {
							if (++i >= s.length()) {
								return s; // error
							}
							switch (ch = s.charAt(i)) {
								case 'b':
									ch = '\b';
									break;
								case 'f':
									ch = '\f';
									break;
								case 'n':
									ch = '\n';
									break;
								case 'r':
									ch = '\r';
									break;
								case 't':
									ch = '\t';
									break;
								case '/':
									ch = '/';
									break;
								case '"':
									ch = '"';
									break;
								case 'u':
									try {
										ch = (char) Short.parseShort(
											s.substring(i+1, i+5), 16);
										i += 4;
										break;
									} catch (Exception ex) {
										return s;
									}
								default:
									return s; // error???
							}
						}
						sb.append(ch);
					}
					return sb.toString();
				}
			default: return s;
		}
	}

	/** Add JSON value to the JSON array.
	 * @param array JSON array.
	 * @param s trimmed string with JSON value.
	 */
	private static void valueToArray(final List<Object> array,
		final String s){
		StringParser p = new StringParser(s);
		if (p.isToken("null")) {
			array.add(null);
		} else if (p.isToken("false")) {
			array.add(Boolean.FALSE);
		} else if (p.isToken("true")) {
			array.add(Boolean.TRUE);
		} else if (p.isSignedFloat()) {
			array.add(new BigDecimal(p.getParsedString()));
		} else if (p.isSignedInteger()) {
			array.add(new BigInteger(p.getParsedString()));
		} else if (p.isChar('"')) { // quoted string
			if (s.endsWith("\"")) {
				if (s.length() == 1) {
					array.add(s);
				} else {
					array.add(s.substring(1, s.length() - 1));
				}
			} else {
				StringBuilder sb = new StringBuilder();
				for (;;) {
					if (p.eos()) {
						//End of string is missing
						throw new SRuntimeException(JSON.JSON001);
					}
					if (p.isChar('"')) {
						array.add(sb.toString());
						break;
					}
					if (p.isChar('\\')) {
						p.nextChar();
					}
					sb.append(p.peekChar());
				}
			}
		} else { //not quoed string
			array.add(s);
		}
	}

	/** Create JSON array from element.
	 * @param e element from which the array will be created.
	 * @return JSON array with values created from argument.
	 */
	private List<Object> createArray(final Element e) {
		NodeList nl = e.getChildNodes();
		List<Object> list = new ArrayList<Object>();
		for (int i = 0; i < nl.getLength(); i++) {
			Node n = nl.item(i);
			if (n.getNodeType() == Node.TEXT_NODE
				|| n.getNodeType() == Node.CDATA_SECTION_NODE) {
				String s = n.getNodeValue();
				if (s == null) {
					s = "";
				}
				while (i+1 < nl.getLength()
					&& ((n = nl.item(i+1)).getNodeType() == Node.TEXT_NODE
					|| n.getNodeType() == Node.CDATA_SECTION_NODE)) {
					s += n.getNodeValue();
					i++;
				}
				if (!(s = s.trim()).isEmpty()) {
					valueToArray(list, s);
				}
			} else if (n.getNodeType() == Node.ELEMENT_NODE) {
				list.add(createItem(n));
			}
		}
		return list;
	}

	/** Create JSON map from element.
	 * @param e element from which the map will be created.
	 * @return JSON map created from NodeList.
	 */
	private Map<String,Object> createMap(final Element e) {
		Map<String,Object> map = new LinkedHashMap<String,Object>();
		// process attributes
		NamedNodeMap nnm = e.getAttributes();
		for (int i = 0; i < nnm.getLength(); i++) {
			Node n = nnm.item(i);
			String u = n.getNamespaceURI();
			String name = n.getNodeName();
			if (!_isW3C && !_jsNamespace.equals(u)
				&& !((name.startsWith("xmlns:") || "xmlns".equals(name))
				&& _jsNamespace.equals(n.getNodeValue()))) {
				map.put(toJsonName(name), getJValue(n.getNodeValue()));
			}
		}
		// process child nodes
		NodeList nl = e.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node n = nl.item(i);
			if (n.getNodeType() == Node.ELEMENT_NODE) {
				Element ee = (Element) n;
				Object o = createItem(ee);
				if (_isW3C) {
					String key = ee.getAttribute(J_KEYATTRW3C);
					map.put(key, o);
					continue;
				}
				if (o instanceof Map) { // map created from element
					String k = toJsonName(ee.getNodeName());
					map.put(k, ((Map) o).get(k));
				} else if (o instanceof List && ((List) o).size() >= 1 &&
					((List) o).get(0) instanceof Map) {
					Iterator<?> x =
						((Map)((List)o).get(0)).entrySet().iterator();
					while (x.hasNext()) {
						Map.Entry<?, ?> y = (Map.Entry) x.next();
						List<?> list = (List) o;
						if (list.size() == 1) {
							map.put(y.getKey().toString(), y.getValue());
						} else {
							List<Object> z= new ArrayList<Object>();
							for (int k = 1; k <  list.size(); k++) {
								z.add(list.get(k));
							}
							map.put(y.getKey().toString(), z);
						}
					}
				} else {
					map.put(toJsonName(n.getNodeName()), o);
				}
			} else if (n.getNodeType() == Node.TEXT_NODE
				&& n.getNodeValue() != null
				&& !n.getNodeValue().trim().isEmpty()) {
				//Test node not allowed in this XML representation of JSOM map.
				throw new SRuntimeException(JSON.JSON016);
			}
		}
		return map;
	}

	/** Create JSON item from XML node.
	 * @param n XML node.
	 * @return JSON item
	 */
	private Object createItem(final Node n) {
		switch (n.getNodeType()) {
			case Node.ELEMENT_NODE: {
				Element e = (Element) n;
				if (_jsNamespace.equals(e.getNamespaceURI())) {
					String name = e.getLocalName();
					if (J_ARRAY.equals(name)) {
						return createArray(e);
					}
					if (J_MAP.equals(name)) {
						return createMap(e);
					}
					if (J_NULL.equals(name)) {
						return null;
					} else if (J_STRING.equals(name)
						|| J_NUMBER.equals(name)
						|| J_BOOLEAN.equals(name)
						|| J_ITEM.equals(name)) {
						String s = ((Element) n).getTextContent();
						return getJValue(s);
					} else if (J_ITEM.equals(name)) {
						String s = ((Element) n).getTextContent();
						return getJValue(s);
					}
					// Illegal JSON XML model &{0}
					throw new SRuntimeException(XDEF.XDEF313, n.getNodeName());
				} else {
					return getJsonObject((Element) n);
				}
			}
			case Node.TEXT_NODE:
			case Node.CDATA_SECTION_NODE:
				List<Object> list = new ArrayList<Object>();
				String s = n.getNodeValue();
				if (s == null) {
					s = "";
				}
				valueToArray(list, s.trim());
				if (list.size() == 1) {
					return list.get(0);
				}
				return list;
		}
		// Illegal JSON XML model &{0}
		throw new SRuntimeException(XDEF.XDEF313, n.getNodeName());
	}

	/** Convert XML element to object with JSON data.
	 * @param e XML element.
	 * @return object with JSON data.
	 */
	private Object getJsonObject(final Element e) {
		if (_jsNamespace.equals(e.getNamespaceURI())) {
			if (J_ARRAY.equals(e.getLocalName())) {
				return createArray(e);
			}
			if (J_MAP.equals(e.getLocalName())) {
				return createMap(e);
			}
			//Incorrect XML node name with JSON namespace &{0}{: }
			throw new SRuntimeException(JSON.JSON013, e.getNodeName());
		}
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		String key = toJsonName(e.getNodeName());
		int numOfItems = 0;
		NodeList nl = e.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node n = nl.item(i);
			if (n.getNodeType() == Node.TEXT_NODE) {
				if (n.getNodeValue().trim().isEmpty()) {
					continue;
				}
				numOfItems++;
			} else if (n.getNodeType() == Node.ELEMENT_NODE) {
				numOfItems++;
			}
		}
		Map<String, Object> attrs = genAttrs(e);
		if (attrs != null) {
			map.put(key, attrs);
		}
		if (numOfItems == 0) {
			map.put(key, attrs);
			return map;
		} else {
			List<Object> list = new ArrayList<Object>();
			int i = 0;
			int len = nl.getLength();
			for (; i < len; i++) {
				Element ee;
				Node n = nl.item(i);
				if (n.getNodeType() == Node.TEXT_NODE
					|| n.getNodeType() == Node.CDATA_SECTION_NODE) {
					String s = n.getNodeValue();
					if (s == null) {
						s = "";
					}
					while (i+1 < nl.getLength()
						&& ((n = nl.item(i+1)).getNodeType() == Node.TEXT_NODE
						|| n.getNodeType() == Node.CDATA_SECTION_NODE)) {
						s += n.getNodeValue();
						i++;
					}
					if (!(s = s.trim()).isEmpty()) {
						valueToArray(list, s);
					}
				} else if (n.getNodeType() == Node.ELEMENT_NODE) {
					ee = (Element) n;
					if (_jsNamespace.equals(ee.getNamespaceURI())) {
						Object o = getJsonObject(ee);
						map.put(key, o);
					} else {
						String key1 = toJsonName(ee.getNodeName());
						Object o = getJsonObject(ee);
						if (o instanceof Map) {
							Map m = (Map) o;
							if (attrs == null) {
								attrs = map;
								map.put(key, o);
							} else {
								for (Object x: m.entrySet()){
									Map.Entry entry = (Map.Entry) x;
									attrs.put((String) entry.getKey(),
										entry.getValue());
								}
							}
						} else if (o instanceof List) {
							map.put(key1, o);
						}
					}
				}
			}
			if (len == 1 && i == 1 && !map.containsKey(key)
				&& !_jsNamespace.equals(e.getNamespaceURI())
				&& !J_ARRAY.equals(e.getLocalName())) {
				// no js:array and no child nodes (just an element)
				if (list.size() == 1) {
					map.put(key, list.get(0));
				} else {
					map.put(key, list);
				}
			}
			return map;
		}
	}

	/** Convert XML element to object with JSON data.
	 * @param e XML element.
	 * @return object with JSON data.
	 */
	public static final Object toJson(final Element e) {
		XmlToJson jx = new XmlToJson();
		if (XDConstants.JSON_NS_URI_W3C.equals(e.getNamespaceURI())) {
			jx._jsNamespace = XDConstants.JSON_NS_URI_W3C;
			jx._isW3C = true;
		} else {
			jx._jsNamespace = XDConstants.JSON_NS_URI;
			jx._isW3C = false;
		}
		return jx.getJsonObject(e);
	}
}