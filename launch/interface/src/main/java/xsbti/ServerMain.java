package xsbti;

/** The main entry point for a launched service.
 * 
 * A class implementing this must:
 * 
 * 1. Expose an HTTP port that clients can listen on, returned via the start method.
 * 2. Accept HTTP HEAD requests against the returned URI. These are used as "ping" messages to ensure
 *    a server is still alive, when new clients connect.
 * 3. Create a new thread to execute its service
 */
public interface ServerMain {
  /**
   * This method should launch a new thread(s) which run the service.  After the service has
   * been started, it should return the port
   * 
   * @param configuration
   *          The configuration used to launch this service.
   * @return
   *    A URI denoting the Port which clients can connect to.  Note:  only HTTP protocol and 
   *    localhost/127.0.0.1/::1 addresses are supported in the URI. Any other return value will
   *    cause this service to be shutdown.
   */
  public java.net.URI start(ServerConfiguration configuration);
}