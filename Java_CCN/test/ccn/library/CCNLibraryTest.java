/**
 * 
 */
package test.ccn.library;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.SignatureException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import junit.framework.Assert;

import org.junit.Test;

import com.parc.ccn.CCNBase;
import com.parc.ccn.Library;
import com.parc.ccn.data.ContentName;
import com.parc.ccn.data.ContentObject;
import com.parc.ccn.data.MalformedContentNameStringException;
import com.parc.ccn.data.query.BasicInterestListener;
import com.parc.ccn.data.query.Interest;
import com.parc.ccn.data.security.PublisherPublicKeyDigest;
import com.parc.ccn.data.security.SignedInfo;
import com.parc.ccn.data.util.DataUtils;
import com.parc.ccn.library.CCNFlowControl;
import com.parc.ccn.library.CCNLibrary;
import com.parc.ccn.library.io.CCNWriter;
import com.parc.ccn.library.profiles.SegmentationProfile;
import com.parc.ccn.library.profiles.VersionMissingException;
import com.parc.ccn.library.profiles.VersioningProfile;


/**
 * @author briggs,smetters,rasmusse
 *
 */
public class CCNLibraryTest extends LibraryTestBase {
	static final String contentString = "This is a very small amount of content";
	
	private class NameSeen {
		private ContentName name;
		private boolean seen = false;
		
		private NameSeen(ContentName name) {
			this.name = name;
		}
		
		private boolean setSeen(ContentObject co) {
			if (name.isPrefixOf(co.name())) {
				seen = true;
				return true;
			}
			return false;
		}
	}
	
	@Test
	public void testEnumerate() {
		Assert.assertNotNull(putLibrary);
		Assert.assertNotNull(getLibrary);

		try {
			
			CCNWriter writer = new CCNWriter("/CPOF", putLibrary);
			ArrayList<NameSeen> testNames = new ArrayList<NameSeen>(3);
			testNames.add(new NameSeen(ContentName.fromNative("/CPOF/foo")));
			testNames.add(new NameSeen(ContentName.fromNative("/CPOF/bar/lid")));
			testNames.add(new NameSeen(ContentName.fromNative("/CPOF/bar/jar")));
			
			for (int i = 0; i < testNames.size(); i++) {
				writer.put(testNames.get(i).name, Integer.toString(i).getBytes());
			}
			
			ArrayList<ContentObject> availableNames =
				getLibrary.enumerate(new Interest("/CPOF"), CCNLibrary.NO_TIMEOUT);

			Iterator<ContentObject> nameIt = availableNames.iterator();

			while (nameIt.hasNext()) {
				ContentObject theName = nameIt.next();

				// Just get by name, to test equivalent to current
				// ONC interface.
				ContentObject theObject = getLibrary.get(theName.name(), 1000);

				if (null == theObject) {
					System.out.println("Missing content: enumerated name: " + theName.name() + " not gettable.");

				} else {
					System.out.println("Retrieved name: " + theName.name());
				}
				
				for (NameSeen nt : testNames) {
					if (nt.setSeen(theName))
						break;
				}
			}
			
			for (NameSeen nt : testNames) {
				if (!nt.seen)
					Assert.fail("Didn't see name " + nt.name.toString() + " in enumeration");
			}

		} catch (Exception e) {
			System.out.println("Got an exception in enumerate test: " + e.getClass().getName() + ": " + e.getMessage());
			e.printStackTrace();
			Assert.fail("Exception in testEnumerate: " + e.getMessage());
		}
	}

	@Test
	public void testPut() {
		Assert.assertNotNull(putLibrary);
		Assert.assertNotNull(getLibrary);

		ContentName name = null;
		byte[] content = null;
//		SignedInfo.ContentType type = SignedInfo.ContentType.LEAF;
		PublisherPublicKeyDigest publisher = null;

		try {
			content = contentString.getBytes("UTF-8");	
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		try {
			name = ContentName.fromNative("/test/briggs/foo.txt");
		} catch (MalformedContentNameStringException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			CCNWriter segmenter = new CCNWriter(name, putLibrary);
			ContentName result = segmenter.put(name, content, publisher);
			System.out.println("Resulting ContentName: " + result);
		} catch (SignatureException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testRevision() {
		String key = "/test/key";
		byte[] data1 = "data".getBytes();
		byte[] data2 = "newdata".getBytes();
		ContentName revision1;
		ContentName revision2;

		try {
			ContentName keyName = ContentName.fromNative(key);
			CCNWriter segmenter = new CCNWriter(keyName, putLibrary);
			revision1 = segmenter.newVersion(keyName, data1);
			revision2 = segmenter.newVersion(keyName, data2);
			long version1 = VersioningProfile.getLastVersionAsLong(revision1);
			long version2 = VersioningProfile.getLastVersionAsLong(revision2);
			System.out.println("Version1: " + version1 + " version2: " + version2);
			Assert.assertTrue("Revisions are strange", 
					version2 > version1);
		} catch (Exception e) {
			System.out.println("Exception in updating versions: " + e.getClass().getName() + ": " + e.getMessage());
			Assert.fail(e.getMessage());
		}	
	}

	@Test
	public void testVersion() throws Exception {

		String name = "/test/smetters/stuff/versioned_name";
		ContentName cn = ContentName.fromNative(name);
		String name2 = "/test/smetters/stuff/.directory";
		ContentName cn2 = ContentName.fromNative(name2);
		String data = "The associated data.";
		String newdata = "The new associated data.";

		versionTest(cn, data.getBytes(), newdata.getBytes());
		versionTest(cn2, data.getBytes(), newdata.getBytes());

	}

	@Test
	public void testGetLatestVersion() throws Exception {
		String name = "/test/simon/versioned_name-" + new Random().nextInt(10000);
		// include a base object, who's digest can potentially confuse getLatestVersion
		ContentName base = ContentName.fromNative(name);
		
		// Don't do repeated get latest versions for now, not working.
		final int testCount = 2;
		final byte [][] data = new byte[testCount][1];
		for (int i=0; i < testCount; ++i) {
			data[i][0] = (byte)i;
		}
		CCNFlowControl f = new CCNFlowControl(base, putLibrary);
		ContentObject [] cos = new ContentObject[testCount];
		cos[0] = ContentObject.buildContentObject(base, data[0]);
		for (int i=1; i < testCount; ++i) {
			ContentName versionedName = VersioningProfile.addVersion(base);
			Thread.sleep(3);
			cos[i] = ContentObject.buildContentObject(versionedName, data[i]);
		}
		f.put(cos[0]);
		f.put(cos[1]);
		// java lacks nested functions, so use a class here...
		class t {
			void check(ContentObject o, int i) {
				System.out.println("Got content: " + o.name());
				System.out.println("Original value: " + i + " returned value: " + Byte.toString(o.content()[0]));
				Assert.assertTrue(DataUtils.arrayEquals(o.content(), data[i]));
			}
			/**
			 * Make sure the data is written to ccnd by reading it
			 */
			void readAndCheck(ContentName name, int index) throws IOException {
				System.out.println("Getting content: " + name);
				check(getLibrary.get(name, 2000), index);
			}
		} t test = new t();
		test.readAndCheck(base, 0);
		test.readAndCheck(cos[1].name(), 1);
		test.check(CCNLibrary.getLatestVersion(base, putLibrary.getDefaultPublisher(), 2000, getLibrary), 1);
		// Beef this up a bit...
		for (int i=2; i < testCount; ++i) {
			f.put(cos[i]);
			System.out.println("Wrote content: " + cos[i].name());
			test.check(CCNLibrary.getLatestVersion(cos[i-1].name(), putLibrary.getDefaultPublisher(), 2000, getLibrary), i);
		}
	}

	@Test
	public void testRecall() {
		String key = "/test/smetters/values/data";
		Timestamp time = SignedInfo.now();
		try {
			ContentName keyName = ContentName.fromNative(key);
			CCNWriter writer = new CCNWriter(keyName, putLibrary);
			ContentName name = writer.put(keyName, BigInteger.valueOf(time.getTime()).toByteArray());
			System.out.println("Put under name: " + name);
			ContentObject result = getLibrary.get(name, CCNBase.NO_TIMEOUT);

			System.out.println("Querying for returned name, Got back: " + (result == null ? "0"  : "1") + " results.");

			if (result == null) {
				System.out.println("Didn't get back content we just put in.");
				System.out.println("Put under name: " + keyName);
				System.out.println("Final name: " + name);
				//Assert.fail("Didn't get back content we just put!");

				result = getLibrary.get(name, CCNBase.NO_TIMEOUT);

				System.out.println("Recursive querying for returned name, Got back: " + (result == null ? "0"  : "1") + " results.");

				ContentName parentName = name.parent();
				System.out.println("Inserted name's parent same as key name? " + parentName.equals(keyName));

			} else {
				byte [] content = result.content();
				System.out.println("Got content: " + result.name());
				System.out.println("Original time: " + time + " returned time: " + new Timestamp(new BigInteger(1, content).longValue()));
				Assert.assertNotNull("No content associated with name we just put!", content);
				Assert.assertTrue("didn't get back same data", 
						time.equals(new Timestamp(new BigInteger(1, content).longValue())));
			}

			result = getLibrary.get(keyName, CCNBase.NO_TIMEOUT);

			System.out.println("Querying for inserted name, Got back: " 
							+ (result == null ? "0"  : "1") + " results.");

			if (result == null)
				Assert.fail("Didn't get back content we just put!");

			if (SegmentationProfile.segmentRoot(result.name()).equals(name) &&
					time.equals(new Timestamp(new BigInteger(1, result.content()).longValue()))) {
				System.out.println("Got back name we inserted.");
			} else {
				Library.logger().warning("Didn't get back data we just inserted:\n  result: " + result.name() + 
								" (write time: " + result.signedInfo().getTimestamp() + 
								   " content time: " + new Timestamp(new BigInteger(1, result.content()).longValue()) +
										")\n   orig: " + name + 
										" (time: " + time + ")");
				Assert.fail("Didn't get back data we just inserted - result name: " + 
						SegmentationProfile.segmentRoot(result.name()) + 
						", auth: " + result.signedInfo() + " - orig name: " + name + "\n, orig time: " +
						time + " time content: " + 
						new Timestamp(new BigInteger(1, result.content()).longValue()));
			}
		} catch (Exception e) {
			System.out.println("Exception in testing recall: " + e.getClass().getName() + ": " + e.getMessage());
			Assert.fail(e.getMessage());
		}
	}

	public void versionTest(ContentName docName,
			byte [] content1,
			byte [] content2) throws Exception {

		CCNWriter writer = new CCNWriter(docName, putLibrary);
		ContentName version1 = writer.newVersion(docName, content1);
		System.out.println("Inserted first version as: " + version1);
		Assert.assertNotNull("New version is null!", version1);

		ContentObject latestVersion =
			CCNLibrary.getLatestVersion(docName, null, CCNLibrary.NO_TIMEOUT, getLibrary);

		Assert.assertNotNull("Retrieved latest version of " + docName + " got null!", latestVersion);
		System.out.println("Latest version name: " + latestVersion.name());

		ContentName version2 = 
			writer.newVersion(docName, content2);

		Assert.assertNotNull("New version is null!", version2);
		System.out.println("Inserted second version as: " + version2);

		ContentObject newLatestVersion = 
			CCNLibrary.getLatestVersion(docName, null, CCNLibrary.NO_TIMEOUT, getLibrary);
		Assert.assertNotNull("Retrieved new latest version of " + docName + " got null!", newLatestVersion);
		System.out.println("Latest version name: " + newLatestVersion.name());

		Assert.assertTrue("Version is not a version of the parent name!", VersioningProfile.isVersionOf(version1, docName));
		Assert.assertTrue("Version is not a version of the parent name!", VersioningProfile.isVersionOf(version2, docName));
		Assert.assertTrue("Version numbers don't increase!", VersioningProfile.getLastVersionAsLong(version2) > VersioningProfile.getLastVersionAsLong(version1));
	}

	@Test
	public void testNotFound() throws Exception {
		try {
			String key = "/some_strange_key_we_should_never_find";
			ContentObject result = getLibrary.get(ContentName.fromNative(key), 1000);
			Assert.assertTrue("found something when there shouldn't have been anything", result == null);
		} catch (Exception e) {
			System.out.println("Exception in testing recall: " + e.getClass().getName() + ": " + e.getMessage());
			Assert.fail(e.getMessage());
		}
	}

	class TestListener extends BasicInterestListener {
		int _count = 0;
		Thread _mainThread;

		public TestListener(CCNBase queryProvider,
				Interest initialInterest,
				Thread mainThread) {
			super(queryProvider);
			_mainThread = mainThread;
		}

		public Interest handleContent(ArrayList<ContentObject> results, Interest interest) {
			byte[] content = null;
			try {
				if (null != results) {
					Iterator<ContentObject> rit = results.iterator();
					while (rit.hasNext()) {
						ContentObject co = rit.next();
						System.out.println("handleContent: got " + co.name());

						content = co.content();
						String strContent = new String(content);
						
						if (VersioningProfile.hasTerminalVersion(co.name())) {
							// We're writing this content using CCNWriter.put. That interface
							// does *not* version content for you, at least at the moment. 
							// TODO We need to decide whether we expect it to. So don't require
							// versioning here yet. 
							System.out.println("Got update for " + co.name() + ": " + strContent + 
								" (revision " + VersioningProfile.getLastVersionAsLong(co.name()) + ")");
						} else {
							System.out.println("Got update for " + co.name() + ": " + strContent);
						}
						_count++;
						switch(_count) {
						case 1:
							Assert.assertEquals("data1", strContent);
							System.out.println("Got data1 back!");
							_mainThread.interrupt();
							break;
						case 2: 
							Assert.assertEquals("data2", strContent);
							System.out.println("Got data2 back!");
							_mainThread.interrupt();
							break;
						default:
							Assert.fail("Somehow got a third update");
						}
					}
				}
			} catch (VersionMissingException vex) {
				Assert.fail("No version when expecting one -- though be careful to make sure we should have been.");
			}
			return null;
		}
	}

	@Test
	public void testInterest() {
		String key = "/test/interest";
		final Thread mainThread = Thread.currentThread();

		byte[] data1 = "data1".getBytes();
		byte[] data2 = "data2".getBytes();

		try {
			CCNWriter writer = new CCNWriter(key, putLibrary);
			Interest ik = new Interest(key);
			TestListener tl = new TestListener(getLibrary, ik, mainThread);
			getLibrary.expressInterest(ik, 
					tl);
			writer.put(ContentName.fromNative(key), data1);
			// wait a little bit before we move on...
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}

			writer.put(ContentName.fromNative(key), data2);

			// wait a little bit before we move on...
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}

			getLibrary.cancelInterest(ik, tl);

		} catch (Exception e) {
			System.out.println("Exception in testing interests: " + e.getClass().getName() + ": " + e.getMessage());
			Assert.fail(e.getMessage());
		}
	}

}
