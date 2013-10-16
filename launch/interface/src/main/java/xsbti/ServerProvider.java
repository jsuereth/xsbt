package xsbti;

import java.io.File;

/** This is an interface which can be used to construct new applications and inspect
 *  the things used to construct that application. 
 */
public interface ServerProvider
{
	/** Returns the ScalaProvider that this AppProvider will use. */
	public ScalaProvider scalaProvider();
	/** The ID of the application that will be created by 'newServer' or 'serverClass'.*/
	public ApplicationID id();
	/** The classloader used to launch this application. */
	public ClassLoader loader();
	/** Loads the class for the entry point for the application given by 'id'.  This method will return the same class
	* every invocation.  That is, the ClassLoader is not recreated each call.*/
	public Class<? extends ServerMain> mainClass();
	/** Creates a new instance of the entry point of the application given by 'id'.
	* It is guaranteed that newMain().getClass() == mainClass()*/
	public ServerMain newMain();	
	/** The classpath from which the main class is loaded, excluding Scala jars.*/
	public File[] mainClasspath();

	public ComponentProvider components();
}
