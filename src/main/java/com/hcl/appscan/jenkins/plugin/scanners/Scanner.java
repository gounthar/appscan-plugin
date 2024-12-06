/**
 * @ Copyright IBM Corporation 2016.
 * @ Copyright HCL Technologies Ltd. 2017, 2024.
 * LICENSE: Apache License, Version 2.0 https://www.apache.org/licenses/LICENSE-2.0
 */

package com.hcl.appscan.jenkins.plugin.scanners;

import com.hcl.appscan.jenkins.plugin.Messages;
import com.hcl.appscan.jenkins.plugin.auth.JenkinsAuthenticationProvider;
import com.hcl.appscan.sdk.CoreConstants;
import com.hcl.appscan.sdk.logging.IProgress;
import com.hcl.appscan.sdk.logging.Message;
import com.hcl.appscan.sdk.utils.ServiceUtil;
import hudson.AbortException;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.util.VariableResolver;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.regex.Pattern;

public abstract class Scanner extends AbstractDescribableImpl<Scanner> implements ScannerConstants, Serializable {

	private static final long serialVersionUID = 1L;
	
	private String m_target;
	private boolean m_hasOptions;
	
	public Scanner(String target, boolean hasOptions) {
		m_target = target;
		m_hasOptions = hasOptions;
	}
	
	public boolean getHasOptions() {
		return m_hasOptions;
	}
	
	public String getTarget() {
		return m_target;
	}
	
	public abstract Map<String, String> getProperties(VariableResolver<String> resolver) throws AbortException;

	public abstract void validateSettings(JenkinsAuthenticationProvider authProvider, Map<String, String> properties, IProgress progress, boolean isAppScan360) throws IOException;

	public abstract String getType();

	public boolean isNullOrEmpty(String string) { return string != null && !string.trim().isEmpty(); }
	
	protected String resolvePath(String path, VariableResolver<String> resolver) {
		//First replace any variables in the path
		path = Util.replaceMacro(path, resolver);
		Pattern pattern = Pattern.compile("^(\\\\|/|[a-zA-Z]:\\\\)");

		//If the path is not absolute, make it relative to the workspace
		if(!pattern.matcher(path).find()){
			String targetPath = "${WORKSPACE}" + "/" + path ;
			targetPath = Util.replaceMacro(targetPath, resolver);
			return targetPath;
		}

		return path;
	}
    protected void validateGeneralSettings(JenkinsAuthenticationProvider authProvider, Map<String, String> properties, IProgress progress, boolean isAppScan360) throws IOException {
        if (isAppScan360) {
            if (!properties.get("FullyAutomatic").equals("true")) {
                progress.setStatus(new Message(Message.WARNING, Messages.warning_allow_intervention_AppScan360()));
            }
        } else if (authProvider.getacceptInvalidCerts()) {
            progress.setStatus(new Message(Message.WARNING, Messages.warning_asoc_certificates()));
        }

        if(properties.containsKey(CoreConstants.SCAN_ID)) {
            if(properties.get(CoreConstants.PERSONAL_SCAN).equals("true")) {
                progress.setStatus(new Message(Message.WARNING, Messages.warning_personal_scan_rescan()));
            }
        }
    }

    protected void scanIdValidation(JSONObject scanDetails, Map<String, String> properties) throws JSONException, IOException {
        if(scanDetails == null) {
            throw new AbortException(Messages.error_invalid_scan_id());
        } else {
            String status = scanDetails.getJSONObject("LatestExecution").getString("Status");
            if(!(status.equals("Ready") || status.equals("Paused") || status.equals("Failed"))) {
                throw new AbortException(Messages.error_scan_id_validation_status(status));
            } else if (!scanDetails.get("RescanAllowed").equals(true) && scanDetails.get("ParsedFromUploadedFile").equals(true)) {
                throw new AbortException(Messages.error_scan_id_validation_rescan_allowed());
            } else if (!scanDetails.get(CoreConstants.APP_ID).equals(properties.get(CoreConstants.APP_ID))) {
                throw new AbortException(Messages.error_invalid_scan_id_application());
            }
        }
    }


}
