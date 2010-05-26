package org.ccnx.ccn.profiles.security.access;

import org.ccnx.ccn.profiles.CCNProfile;
import org.ccnx.ccn.profiles.CommandMarker;
import org.ccnx.ccn.profiles.VersioningProfile;
import org.ccnx.ccn.profiles.namespace.NamespaceProfile;
import org.ccnx.ccn.protocol.ContentName;

public class AccessControlProfile implements CCNProfile {

	public static final CommandMarker ACCESS_CONTROL_MARKER = 
		CommandMarker.commandMarker(CommandMarker.MARKER_NAMESPACE, "ACCESS");
	public static final byte [] ACCESS_CONTROL_MARKER_BYTES = ACCESS_CONTROL_MARKER.getBytes();

	public static final String ROOT_NAME = "ROOT";
	public static final byte [] ROOT_NAME_BYTES = ContentName.componentParseNative(ROOT_NAME);
	public static final String DATA_KEY_NAME = "DK";
	public static final byte [] DATA_KEY_NAME_BYTES = ContentName.componentParseNative(DATA_KEY_NAME);
	public static final String ACCESS_CONTROL_POLICY_NAME = "AccessControl";
	public static final byte [] ACCESS_CONTROL_POLICY_NAME_BYTES = ACCESS_CONTROL_POLICY_NAME.getBytes();
	protected static final ContentName ACCESS_CONTROL_POLICY_CONTENTNAME = new ContentName(new byte [][] {ACCESS_CONTROL_POLICY_NAME_BYTES});

	/**
	 * Used by KeyDirectory
	 */
	public static final String SUPERSEDED_MARKER = "SupersededBy";
	public static final String PREVIOUS_KEY_NAME = "PreviousKey";
	public static final String GROUP_PUBLIC_KEY_NAME = "Key";
	public static final String GROUP_PRIVATE_KEY_NAME = "PrivateKey";

	protected static final ContentName ROOT_POSTFIX_NAME = new ContentName(new byte [][] {ACCESS_CONTROL_MARKER_BYTES, ROOT_NAME_BYTES});

	/**
	 * Returns whether the specified name contains the access control marker
	 * @param name the name
	 * @return
	 */
	public static boolean isAccessName(ContentName name) {
		return name.contains(ACCESS_CONTROL_MARKER_BYTES);
	}

	/**
	 * Truncates the specified name at the access control marker
	 * @param name the name
	 * @return the truncated name
	 */
	public static ContentName accessRoot(ContentName name) {
		return name.cut(ACCESS_CONTROL_MARKER_BYTES);
	}

	/**
	 * Return the name of the root access control policy information object,
	 * if it is stored at node nodeName.
	 **/
	public static ContentName rootName(ContentName nodeName) {
		ContentName baseName = (isAccessName(nodeName) ? accessRoot(nodeName) : nodeName);
		ContentName aclRootName = baseName.append(rootPostfix());
		return aclRootName;
	}
	
	/**
	 * Return the set of name components to add to an access root to get the root name.
	 */
	public static ContentName rootPostfix() {
		return ROOT_POSTFIX_NAME;
	}

	/**
	 * Get the name of the data key for a given content node.
	 * This is nodeName/_access_/DK.
	 * @param nodeName the name of the content node
	 * @return the name of the corresponding data key
	 */
	public static ContentName dataKeyName(ContentName nodeName) {
		ContentName baseName = accessRoot(nodeName);
		ContentName dataKeyName = new ContentName(baseName, ACCESS_CONTROL_MARKER_BYTES, DATA_KEY_NAME_BYTES);
		return dataKeyName;
	}

	/**
	 * Returns whether the specified name is a data key name
	 * @param name the name
	 * @return
	 */
	public static boolean isDataKeyName(ContentName name) {
		if (!isAccessName(name) || VersioningProfile.hasTerminalVersion(name)) {
			return false;
		}
		int versionComponent = VersioningProfile.findLastVersionComponent(name);
		if (name.stringComponent(versionComponent - 1).equals(DATA_KEY_NAME)) {
			return true;
		}
		return false;
	}
	
	/**
	 * Return the name of the access control policy under a known policy prefix.
	 * @param policyPrefix
	 * @return
	 */
	public static final ContentName getAccessControlPolicyName(ContentName policyPrefix) {
		return new ContentName(policyPrefix, ACCESS_CONTROL_POLICY_NAME_BYTES);
	}

	public static ContentName AccessControlPolicyContentName() {
		return ACCESS_CONTROL_POLICY_CONTENTNAME;
	}
	
	/**
	 * Returns whether the specified name contains the access control policy marker
	 * @param name the name
	 * @return
	 */
	public static boolean isAccessControlPolicyName(ContentName name) {
		return name.contains(ACCESS_CONTROL_POLICY_NAME_BYTES);
	}
	
	/**
	 * Get the policy marker name for a given namespace to access control.
	 */
	public static ContentName getAccessControlPolicyMarkerName(ContentName accessControlNamespace) {
		ContentName policyPrefix = NamespaceProfile.policyNamespace(accessControlNamespace);
		ContentName policyMarkerName = AccessControlProfile.getAccessControlPolicyName(policyPrefix);
		return policyMarkerName;
	}
	
}
