package driver;

import client.Client;
import client.ClientPoP3;
import client.SMTPClient;

public class Main {
    public static void main(String[] args)  {
        Client cl = new ClientPoP3();
        cl.startClient();
    }
}