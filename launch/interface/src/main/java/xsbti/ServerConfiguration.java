package xsbti;

import java.io.File;

/** This is the configuration passed into a ServerMain instance.  It should
 * contain all access to launcher features and configuration needed to
 * launch servers.
 */
public interface ServerConfiguration
{
	public String[] arguments();
	public File baseDirectory();
	public ServerProvider provider();
}