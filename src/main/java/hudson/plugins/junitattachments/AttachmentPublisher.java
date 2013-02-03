package hudson.plugins.junitattachments;

import com.thoughtworks.xstream.XStream;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.ClassResult;
import hudson.tasks.junit.TestAction;
import hudson.tasks.junit.TestDataPublisher;
import hudson.tasks.junit.TestObject;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import hudson.util.HeapSpaceStringConverter;
import hudson.util.XStream2;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AttachmentPublisher extends TestDataPublisher {

	private static final Logger LOGGER = Logger.getLogger(AttachmentPublisher.class.getName());

	private static final XStream XSTREAM = new XStream2() {
		{
			registerConverter(new HeapSpaceStringConverter());
		}
	};


	@DataBoundConstructor
	public AttachmentPublisher() {
	}

	public static FilePath getAttachmentPath(AbstractBuild<?, ?> build) {
		return new FilePath(build.getRootDir()).child("junit-attachments");
	}

	@Override
	public Data getTestData(AbstractBuild<?, ?> build, Launcher launcher,
							BuildListener listener, TestResult testResult) throws IOException,
		InterruptedException {
		final GetTestDataMethodObject methodObject = new GetTestDataMethodObject(build, launcher, listener, testResult);
		Map<String, List<String>> attachments = methodObject.getAttachments();

		if (attachments.isEmpty()) {
			return null;
		}

		return new Data(build, attachments);

	}

	public static class Data extends TestResultAction.Data {

		private Map<String, List<String>> attachments;
		private transient Reference<Map<String, List<String>>> attachmentsRef;

		public Data(AbstractBuild<?, ?> build, Map<String, List<String>> attachments) throws IOException {
			new XmlFile(XSTREAM, getFile(build)).write(attachments);
			attachmentsRef = new WeakReference(attachments);
		}

		private File getFile(AbstractBuild<?, ?> build) {
			return new File(build.getRootDir(), "junit-attachments.xml");
		}

		private Map<String, List<String>> getAttachments(AbstractBuild<?, ?> build) {
			if (attachments != null) return attachments;

			if (attachmentsRef == null || attachmentsRef.get() == null) {
				File f = getFile(build);
				try {
					attachmentsRef = new WeakReference<Map<String, List<String>>>((Map<String, List<String>>) new XmlFile(f).read());
				} catch (IOException e) {
					LOGGER.log(Level.WARNING, "could not read junit-attachments.xml for " + build, e);
					attachmentsRef = new WeakReference<Map<String, List<String>>>(Collections.<String, List<String>>emptyMap());
				}
			}
			return attachmentsRef.get();
		}

		@Override
		public List<TestAction> getTestAction(TestObject testObject) {
			ClassResult cr;
			if (testObject instanceof ClassResult) {
				cr = (ClassResult) testObject;
			} else if (testObject instanceof CaseResult) {
				cr = (ClassResult) testObject.getParent();
			} else {
				return Collections.emptyList();
			}


			AbstractBuild<?, ?> build = cr.getOwner();
			Map<String, List<String>> allAttachments = getAttachments(build);

			String className = cr.getParent().getName();
			if (className.equals("(root)")) className = "";   // working around the name mangling for the root package
			if (className.length() > 0) className += '.';
			className += cr.getName();
			List<String> attachments = allAttachments.get(className);
			if (attachments != null) {
				return Collections
					.<TestAction>singletonList(new AttachmentTestAction(
						cr, getAttachmentPath(testObject.getOwner())
						.child(className), attachments));
			} else {
				return Collections.emptyList();
			}

		}
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<TestDataPublisher> {

		@Override
		public String getDisplayName() {
			return "Publish test attachments";
		}

	}

}
