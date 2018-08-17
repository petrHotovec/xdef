/*
 * File: Test002.java
 *
 * Copyright 2007 Syntea software group a.s.
 *
 * This file may be used, copied, modified and distributed only in accordance
 * with the terms of the limited licence contained in the accompanying
 * file LICENSE.TXT.
 *
 * Tento soubor muze byt pouzit, kopirovan, modifikovan a siren pouze v souladu
 * s licencnimi podminkami uvedenymi v prilozenem souboru LICENSE.TXT.
 */
package test.xdef;

import cz.syntea.xdef.XDConstants;
import cz.syntea.xdef.sys.ArrayReporter;
import cz.syntea.xdef.sys.Report;
import cz.syntea.xdef.XDDocument;
import cz.syntea.xdef.XDFactory;
import cz.syntea.xdef.XDPool;
import cz.syntea.xdef.XDValue;
import cz.syntea.xdef.proc.XXData;
import cz.syntea.xdef.proc.XXNode;
import cz.syntea.xdef.sys.FileReportReader;
import cz.syntea.xdef.sys.FileReportWriter;
import cz.syntea.xdef.sys.ReportPrinter;
import cz.syntea.xdef.sys.ReportReader;
import cz.syntea.xdef.sys.ReportWriter;
import cz.syntea.xdef.sys.StringParser;
import cz.syntea.xdef.xml.KXmlUtils;
import test.util.XDefTester;

import java.io.File;
import java.io.FileInputStream;
import org.w3c.dom.Element;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import org.w3c.dom.Document;

/** Class for testing (miscellaneous).
 * @author Vaclav Trojan
 */
public final class Test003 extends XDefTester {

	public Test003() {super();}

	static int _count = 0;
	boolean _myErrFlg;
	int _xx;
	public static boolean skodaTisice(final XXData xdata,final XDValue[]params){
		Test003 test2 = (Test003)xdata.getUserObject();
		if (test2 != null) {
			test2._xx++;
		}
		StringParser p = new StringParser(xdata.getTextValue());
		if (p.isInteger()) {
			if (p.eos()) {
				return true;
			}
			if (p.isChar(',')) {
				if (p.isInteger()) {
					if (p.eos()) {
						return true;
					}
				}
			}
		}
		xdata.error("XDEF532",
			"Error detected by external check method: &{msg}",
			"&{msg}skodaTisice: " + xdata.getTextValue());
		return false;
	}

	public static boolean rodneCislo(final XXData xdata,
		final XDValue[] params) {
		try {
			String value = xdata.getTextValue();
			int rok = Integer.parseInt(value.substring(0,2));
			int mesic = Integer.parseInt(value.substring(2,4));
			if ((mesic < 1) || (mesic > 12 && mesic < 51) || (mesic > 62)) {
				xdata.error("XDEF532",
					"Error detected by external check method: &{msg}",
					"&{msg}RC - mesic:" + mesic);
				return false;
			}
			int den = Integer.parseInt(value.substring(4,6));
			if ((den < 1) || (den > 31)) {
				xdata.error("XDEF532",
					"Error detected by external check method: &{msg}",
					"&{msg}RC - mesic:" + mesic);
				return false;
			}
			int i = 6;
			if (value.charAt(i) == '/') {
				i++;
				if (Character.toUpperCase(value.charAt(i)) == 'C') {
					return true; //cizinec
				}
			}
			int n = Integer.parseInt(value.substring(i));
			if (value.length() > i + 3) {
				int mod =  n % 10;
				int num = rok*10000000+mesic*100000+den*1000+(n/10);
				if (num % 11 != mod) {
					xdata.error("XDEF532",
						"Error detected by external check method: &{msg}",
						"&{msg}RC - CRC");
					return false;
				}
			}
			return true;
		} catch (Exception ex) {
			xdata.error("XDEF532",
				"Error detected by external check method: &{msg}",
				"&{msg}RC - format:" + xdata.getTextValue());
			return false;
		}
	}

	public static boolean skodaText(final XXData xdata, final XDValue[] params){
		if (xdata.getTextValue() != null) {
			return true;
		}
		String s = xdata.getElement().getAttribute("TisKc");
		if (s.length() > 0) {
			xdata.setTextValue("");
			return true;
		}
		return true;
	}

	public static boolean PlatnostPDN(final XDValue[] params) {return true;}

	public static  boolean blobref(final XDValue[] params)	{return true;}

	public static void myErr(XXNode chkElem, XDValue[] params) {
		Test003 test2 = (Test003) chkElem.getUserObject();
		if (test2 != null) {
			test2._myErrFlg = true;
		}
		if ("/SouborD1A/ZaznamPDN[54]/VyliceniDN[1]".equals(chkElem.getXPos())){
			chkElem.error(null,"OK Error myErr: " + chkElem.getXPos());
		} else  {
			chkElem.error(null, "Incorrect Error myErr: " + chkElem.getXPos());
			chkElem.error(null,"Error myErr again");
		}
	}

	public static void trString(final XDValue[] params) {}

	public static void konecZaznamu(final XDValue[] params)	{_count++;}

	@Override
	/** Run tests. */
	public void test() {
		String xdef;
		String xml;
		XDPool xp;
		XDDocument xd;
		ArrayReporter reporter = new ArrayReporter();
		FileOutputStream fw;
		ReportWriter rw;
		InputStreamReader isr;
		Element el;
		final String dataDir = getDataDir() + "test/";
		Report.setLanguage("en"); //localize
		try {
			reporter.clear();
			xp = compile(dataDir + "TestChkParser1_1.xdef");
			xd = xp.createXDDocument("CKP");
			xd.xparse(new File(dataDir + "TestChkParser1_1.xml"), reporter);
			assertNoErrors(reporter);
		} catch(Exception ex) {fail(ex);}
		try {
			// X-definition referred from XML
			reporter.clear();
			XDFactory.xparse(dataDir + "TestChkParser1.xml", reporter);
			assertNoErrors(reporter);
		} catch(Exception ex) {fail(ex);}
		try {
			String defFile = dataDir + "SouborD1A_.xdef";
			setProperty("xdef.externalmode", "both");
			setProperty(XDConstants.XDPROPERTY_XINCLUDE,
				XDConstants.XDPROPERTYVALUE_XINCLUDE_TRUE);
			xp = compile(defFile);
			File tmp1 = File.createTempFile("SouborD1A", "err");
			tmp1.deleteOnExit();
			fw = new FileOutputStream(tmp1);
			rw = new FileReportWriter(fw);
			xd = xp.createXDDocument("SouborD1A");
			if (!"29.5.2003".equals(xd.getImplProperty("date"))) {
				fail(xd.getImplProperty("date"));
			}
			if (!"1.0.0".equals(xd.getImplProperty("version"))) {
				fail(xd.getImplProperty("version"));
			}
			xd.setUserObject(this);
			el = xd.xparse(dataDir + "SouborD1D.xml", rw);
			if (xd.getDocument().getXmlEncoding() != null
				&& !"UTF-8".equalsIgnoreCase(
				el.getOwnerDocument().getXmlEncoding())) {
				fail("encoding: " + xd.getDocument().getXmlEncoding());
			}
			isr = new InputStreamReader(
				new FileInputStream(dataDir + "SouborD1B.xml"));
			fw.close();
			OutputStreamWriter lst;
			FileReader fr;
			ReportReader rr;
			File tmp2 = File.createTempFile("SouborD1A", "err");
			tmp2.deleteOnExit();
			fr = new FileReader(tmp2);
			rr = new FileReportReader(fr, true);
			File tmp3 = File.createTempFile("SouborD1A", "lst");
			tmp3.deleteOnExit();
			lst = new OutputStreamWriter(new FileOutputStream(tmp3));
			ReportPrinter.printListing(lst, isr, rr, null, 80, false, null);
			fr.close();
			isr.close();
			lst.close();
		} catch (Exception ex) {fail(ex);}
		if (XDefTester.getFulltestMode()) {
			try { // test big XML
				xdef =
	"<xd:def xmlns:xd='" + test.util.XDefTester.XDEFNS + "' xd:root=\"koně\">\n"+
	"\n"+
	"  <koně>\n"+
	"    <kůň xd:script = \"occurs *; forget\"\n" +
	"      jaký = \"eq('úplně šílený nóbl žluťoučký kůň')\"\n" +
	"      kde = \"string\"\n" +
	"      barva = \"an\"\n" +
	"      co = \"string(3)\"\n" +
	"      nějaký = \"string(4)\">\n" +
	"      <kam>pic('AAAAAA')</kam>\n" +
	"       string(10,999); fixed 'skákal přes louže'\n" +
	"      <proč>string(7,%pattern=['j.*'])</proč>\n" +
	"    </kůň>\n"+
	"  </koně>\n"+
	"\n"+
	"</xd:def>";
				xp = compile(xdef);
				byte[] child = (
	"  <kůň jaký = \"úplně šílený nóbl žluťoučký kůň\"\r\n" +
	"    kde = \"louže\"\r\n" +
	"    barva = \"žluťoučký\"\r\n" +
	"    co = \"kůň\"\r\n" +
	"    nějaký = \"nóbl\">\r\n" +
	"     <kam>daleko</kam>\n " +
	"     skákal přes louže\n " +
	"     <proč>jen tak</proč>\n " +
	" </kůň>\r\n").getBytes("UTF-8");
				// create big XML file
				//parse created file and get time of processing
				xd = xp.createXDDocument();
				File tempfile = File.createTempFile("bigxml", "xml");
				tempfile.deleteOnExit();
				xml = tempfile.getAbsolutePath();
				FileOutputStream longfile = new FileOutputStream(xml);
				longfile.write(
					"<?xml version = \"1.0\" encoding = \"UTF-8\"?>\r\n".
					getBytes("UTF-8"));
				longfile.write("<koně>\r\n".getBytes("UTF-8"));
				long num = 60000; // 15 Mbytes
				for (int i = 0; i < num; i++) {
					longfile.write(child);
				}
				longfile.write("</koně>\r\n".getBytes("UTF-8"));
				longfile.close();
				long datalen = tempfile.length();
				long t = System.currentTimeMillis();
				xd.xparse(xml, null);
				float duration =
					((float)((System.currentTimeMillis() - t) / 1000.0));
				DecimalFormat df = new DecimalFormat("0.00");
				setResultInfo("Big XML: "
					+ df.format(((float) datalen / 1000.0))
					+ "KB/" + df.format(duration)
					+ "s (" + df.format((datalen / 1000.0)/duration)+"KB/s);");
			} catch (Exception ex) {fail(ex);}
			try {
				_count = 0;
				reporter.clear();
				DecimalFormat df = new DecimalFormat("0.00");
				df.setDecimalSeparatorAlwaysShown(true);
				long t = System.currentTimeMillis();
				xp = compile(dataDir + "SouborD1A.xdef");
				float duration =
					(float) ((System.currentTimeMillis() - t) / 1000.0);
				String durationInfo = "SouborD1A: compile "
					+ df.format(duration)+"s";
				File tmp1 = File.createTempFile("SouborD1A", "err");
				tmp1.deleteOnExit();
				fw = new FileOutputStream(tmp1);
				rw = new FileReportWriter(fw);
				xd = xp.createXDDocument("SouborD1A");
				if (!"29.5.2003".equals(xd.getImplProperty("date"))) {
					fail(xd.getImplProperty("date"));
				}
				if (!"1.0.0".equals(xd.getImplProperty("version"))) {
					fail(xd.getImplProperty("version"));
				}
				xd.setUserObject(this);
				t = System.currentTimeMillis();
				xd.xparse(dataDir + "SouborD1A.xml", rw);
				duration = (float)((System.currentTimeMillis() - t) / 1000.0);
				if (!"windows-1250".equalsIgnoreCase(
					xd.getDocument().getXmlEncoding())) {
					fail("encoding: " + xd.getDocument().getXmlEncoding());
				}
				long datalen = new File(dataDir + "SouborD1A.xml").length();
				durationInfo += "; data " + df.format(((float) datalen/1000.0))
					+ "KB/" + df.format(duration)
					+ "s (" + df.format((datalen / 1000.0)/duration) + "KB/s);";
				isr = new InputStreamReader(new FileInputStream(
					dataDir + "SouborD1A.xml"));
				fw.close();
				FileReader fr = new FileReader(tmp1);
				ReportReader rr = new FileReportReader(fr, true);
				File tmp2 = File.createTempFile("SouborD1A", "lst");
				tmp2.deleteOnExit();
				OutputStreamWriter lst = new OutputStreamWriter(
					new FileOutputStream(tmp2));
				ReportPrinter.printListing(lst, isr, rr, null, 80, false, null);
				fr.close();
				isr.close();
				lst.close();
				assertTrue(rw.getErrorCount() == 361 && _count == 221,
					"expected errors/count: 361/221, found " +
						"detected: " + rw.getErrorCount() + "/" + _count);
				assertTrue(_myErrFlg, "myErr not invoked");
				assertEq(_xx, 673, "nonstatic result 673/" + _xx);
				// Same data, this time called as validation
				_xx = 0;
				_myErrFlg = false;
				_count = 0;
				Document doc = KXmlUtils.parseXml(dataDir + "SouborD1A.xml", false);
				String encoding = doc.getXmlEncoding();
				if (!"windows-1250".equalsIgnoreCase(encoding)) {
					fail("encoding: " + encoding);
				}
				reporter.clear();
				xd = xp.createXDDocument("SouborD1A");
				xd.setUserObject(this);
				xd.xparse(doc, reporter);
				fr = new FileReader(tmp1);
				rr = new FileReportReader(fr, true);
				int count = 0;
				Report report1;
				while ((report1 = reporter.getReport()) != null) {
					Report report2 = rr.getReport();
					if (report2 == null ||
						(report1.getMsgID() != report2.getMsgID() &&
							(report1.getMsgID() == null ||
						!report1.getMsgID().equals(report2.getMsgID())))) {
						fail("Report " + count + "\nparser:     " +
							(report2 == null ? "null" : report2.toString())+ "\n" +
							"validation: " + report1.toString());
						break;
					}
					count++;
				}
				if (!_myErrFlg) {
					fail("myErr not invoked");
				}
				if ( _xx != 673) {
					fail("nonstatic result 673/" + _xx);
				}
				fr.close();
				setResultInfo(durationInfo);
			} catch (Exception ex) {fail(ex);}
		}

		resetTester();
	}

	/** Run test
	 * @param args the command line arguments
	 */
	public static void main(String... args) {
		if (runTest(args) > 0) {System.exit(1);}
	}
}