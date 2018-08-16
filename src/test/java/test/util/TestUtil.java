package test.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.w3c.dom.Element;

import cz.syntea.xdef.XDDocument;
import cz.syntea.xdef.XDFactory;
import cz.syntea.xdef.XDPool;
import cz.syntea.xdef.sys.ReportWriter;
import cz.syntea.xdef.sys.SRuntimeException;
import cz.syntea.xdef.util.gencollection.XDGenCollection;
import cz.syntea.xdef.xml.KXmlUtils;
import test.xdef.Tester;



public class TestUtil {
	
	
	public static URL getResrc(Class<?> clazz, String name) {
		URL url = clazz.getResource(name);
		
		if (url == null) {
			String msg = "resource not found: class=" + clazz.getName() + ", name=" + name;
			logger.debug(msg);
			throw new RuntimeException(msg);
		}
		
		logger.debug("resource found: class=" + clazz.getName() + ", name=" + name);
		
		return url;
	}
	
	
	
	public static void assertNoErrors(final ReportWriter reporter) {
		if (reporter.errorWarnings()) {
			String msg = "XDef-reporter:\n" +
				reporter.getReportReader().printToString() + "\n";
			
			if (reporter.errors()) {
				logger.error(msg);
				Assert.assertTrue(false, msg);
			} else {
				logger.warn(msg);
			}
		}
	}
	
	
	
	public static void assertEq(final String a1, final Element a2) {
		assertEq(KXmlUtils.parseXml(a1).getDocumentElement(), a2);
	}

	/** Check elements.
	 * @param a1 first value.
	 * @param a2 second value.
	 * @param msg message to be printed or null.
	 */
	public static void assertEq(final String a1,
		final Element a2,
		final String msg) {
		assertEq(KXmlUtils.parseXml(a1).getDocumentElement(), a2, msg);
	}

	/** Check elements.
	 * @param a1 first value.
	 * @param a2 second value.
	 */
	public static void assertEq(final Element a1, final String a2) {
		assertEq(a1, KXmlUtils.parseXml(a2).getDocumentElement());
	}

	/** Check elements.
	 * @param a1 first value.
	 * @param a2 second value.
	 * @param msg message to be printed or null.
	 */
	public static void assertEq(final Element a1,
		final String a2,
		final String msg) {
		assertEq(a1, KXmlUtils.parseXml(a2).getDocumentElement(), msg);
	}

	/** Check elements.
	 * @param a1 first value.
	 * @param a2 second value.
	 */
	public static void assertEq(Element a1, Element a2) {assertEq(a1, a2, null);}

	/** Check elements are equal (text nodes are trimmed).
	 * @param a1 first value.
	 * @param a2 second value.
	 * @param msg message to be printed or null.
	 */
	public static void assertEq(final Element a1,
		final Element a2,
		final String msg) {
		assertEq(a1, a2, msg, true);
	}

	public static void assertEq(
		final Element a1,
		final Element a2,
		final Object msg,
		final boolean trim
	) {
		assertNoErrors(KXmlUtils.compareElements(a1, a2, true, null));
	}
	
	
	
	public static XDPool compile(final URL[] urls, final Class<?>... obj) {
		return TestUtil.checkExtObjects(XDFactory.compileXD(_props, urls, obj));
	}
	
	public static XDPool compile(final File file, final Class<?>... obj) throws Exception {
		if (Tester.getFulltestMode()) {
			_xdOfxd.createXDDocument().xparse(genCollection(file.getPath()), null);
		}
		
		return TestUtil.checkExtObjects(XDFactory.compileXD(_props, file, obj));
	}

	public static XDPool compile(final String xdef, final Class<?>... obj) {
		if (Tester.getFulltestMode()) {
			_xdOfxd.createXDDocument().xparse(genCollection(xdef), null);
		}
		return checkExtObjects(XDFactory.compileXD(_props, xdef, obj));
	}

	public static XDPool compile(String[] xdefs, final Class<?>... obj) {
		if (Tester.getFulltestMode()) {
			_xdOfxd.createXDDocument().xparse(genCollection(xdefs), null);
		}
		return checkExtObjects(XDFactory.compileXD(_props, xdefs, obj));
	}

	
	
	public static Element parse(
		final XDPool xp,
		final String defName,
		final String xml,
		final ReportWriter reporter
	) {
		if (reporter != null) {
			reporter.clear();
		}
		XDDocument xd = xp.createXDDocument(defName);
		xd.setProperties(_props);
		Element result = xd.xparse(xml, reporter);
		return result;
	}

	public static Element parse(
		final XDPool xp,
		final String defName,
		final Element el,
		final ReportWriter reporter
	) {
		if (reporter != null) {
			reporter.clear();
		}
		XDDocument xd = xp.createXDDocument(defName);
		xd.setProperties(_props);
		Element result = xd.xparse(el, reporter);
		return result;
	}

	
	
	private static String genCollection(final String... sources) {
		Element el;
		try {
			el = XDGenCollection.genCollection(sources,
				true, //resolvemacros
				true, //removeActions
				false);
		} catch (Exception e) {
			throw new SRuntimeException(e);
		}
		
		return KXmlUtils.nodeToString(el, true);
	}
	
	
	
	public static XDPool checkExtObjects(final XDPool xp) {
		if (!Tester.getFulltestMode()) { return xp; }
		
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			xp.writeXDPool(baos);
			baos.close();
			return XDFactory.readXDPool(
				new ByteArrayInputStream(baos.toByteArray()));
		} catch(RuntimeException e) {
			throw e;
		} catch(Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		} catch(Error e) {
			throw new RuntimeException(e);
		}
	}
	
	
	
    public static String exceptionStackTrace(Throwable ex) {
        
        StringWriter sw = new StringWriter();
        PrintWriter  pw = new PrintWriter(sw);
   
        ex.printStackTrace(pw);
        
        return sw.toString();
    }
	
    
    
    private static XDPool genXdOfXd() {
		String dir = "test/test/xdef/data/test/";
		File f = new File(dir);
		
		if (!f.exists() || !f.isDirectory()) {
			dir = "src/test/java/test/xdef/data/test/";
		}
		
		return XDFactory.compileXD(null,dir+"TestXdefOfXdef*.xdef");
	}
	
	
    public  static final String     XDEFNS  = Tester.XDEFNS;
    
	private static       Properties _props  = new Properties();
	private static final XDPool     _xdOfxd = genXdOfXd();
	/** logger */
	private static final Logger     logger  = LoggerFactory.getLogger(
		TestUtil.class
	);

}
