import java.io.IOException;

public class Main {
  public static void main(String[] args) {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.out.println("Logs from your program will appear here!");

    // TODO: implement parsing using Scanner class
    String directory = null;
    if (args.length == 2 && args[0].equals("--directory")) {
      directory = args[1];
    }

    try {
      HttpServer httpServer = new HttpServer(directory);
      httpServer.start();
    } catch (IOException e) {
      System.err.println("IOException: " + e.getMessage());
    }

  }

}
