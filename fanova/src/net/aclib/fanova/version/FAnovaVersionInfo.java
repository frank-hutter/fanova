package net.aclib.fanova.version;

import org.mangosdk.spi.ProviderFor;

import ca.ubc.cs.beta.aclib.misc.version.AbstractVersionInfo;
import ca.ubc.cs.beta.aclib.misc.version.VersionInfo;

@ProviderFor(VersionInfo.class)
public class FAnovaVersionInfo extends AbstractVersionInfo {

	public FAnovaVersionInfo()
	{
		super("Functional Anova", "fanova-version.txt",true);
	}
}
