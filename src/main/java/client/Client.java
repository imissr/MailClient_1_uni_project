package client;



import util.Utility;

import javax.mail.AuthenticationFailedException;
import javax.mail.Message;
import javax.mail.MessagingException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Console;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import java.util.*;

public abstract class Client {
    /**
     * Scanner to read input from user.
     */
    public  Scanner scanner;
    /**
     * Reader imports bytes from socket
     */
    public BufferedReader bReader;
    /**
     * Writer writes bytes to socket
     */
    public BufferedWriter bWriter;
    /**
     * Client Socket
     */
    public Socket clientSocket;

    /**
     * Signed-in user
     */
    public String username;
    /**
     * User password
     */
    public String password;

    public int socketType;

    public  JavaMail javaMailInstance;

    public int JavaMailtyp;



    /**
     * Standard Constructor.
     */
    public Client()  {
        this.scanner = new Scanner(System.in);
        javaMailInstance = JavaMail.getInstance();
    }


    /**
     * Initiates the first variables in the Client and stars a connection with the POP3 Server.
     * @param domainHost Host/IP of the server.
     * @param port Port
     * @throws UnknownHostException If Domain-name doesn't exists
     * @throws IOException If stream did disconnect or no connection or response from server.
     * @throws IllegalArgumentException If Port-number wasn't according to the range conventions
     */
    private void init(String domainHost, int port) throws UnknownHostException, IOException, IllegalArgumentException {
        switch (socketType){
            case 1 -> this.clientSocket = new Socket(InetAddress.getByName(domainHost), port);
            case 2 -> {
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                this.clientSocket = factory.createSocket(InetAddress.getByName(domainHost), port);
                ((SSLSocket)this.clientSocket).startHandshake();
            }
        }

        this.bReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        this.bWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
    }


   /* public  boolean isSmtp(){
        return this instanceof SmtpCleint;
    }*/






    /**
     * Reads a single line from the buffer.
     * @return The read line that has been read.
     * @throws IOException If connection disconnected, or data retrieval was not possible.
     */
    public abstract String singleLineResponse() throws IOException;



    /**
     * Checks the first Token of a specific server-response
     * @param response The response from the server as a string.
     * @return true if response starts with {@code +OK}, False if response starts with {@code -ERR}
     * @throws RuntimeException if either responses was detected.
     */

    public abstract boolean isOkToken(String response);



    /**
     * Checks the status of the response of the server plus its actual sent message. This method separates the initials
     * from the actual message.
     * @return True if +ok was received, otherwise {@code false}.
     */
    public abstract AbstractMap.SimpleEntry<Boolean, String> readSingleLineContent();

    /**
     * Login prompt that handles user input/output with the server.
     * @return {@code false} if user entered quit, otherwise true.
     */
    private int loginPrompt(){

        while (true){
            System.out.print("\rUsername: ");
            String username = scanner.nextLine();
            if(username.equalsIgnoreCase("quit")) return -1;

            System.out.print("Password: ");
            String password = getPasswordInput();
            if(password.equalsIgnoreCase("quit")) return -1;

            this.username = username; this.password = password;



            switch (socketType){
                case 1, 2 -> {
                    if(!login(username, password)){
                        System.out.println("\rCredentials don't match!\n Try Again");
                        continue;
                    }


                }

                case 3, 4 -> {
                    try {  if(this instanceof ClientPoP3) javaMailInstance.connect(this.username, this.password);
                            else {
                                if(!javaMailInstance.authenticateSMTP(this.username, this.password)){
                                    System.out.println("\rCredentials don't match! Try Again\n");
                                    continue;

                                }else{
                                    javaMailInstance.connectSmtp(this.username, this.password);
                                }
                    }
                    } catch (AuthenticationFailedException e) {
                        System.out.println("\rCredentials don't match! Try Again\n");

                        continue;
                    } catch (MessagingException e){
                        System.out.println("\rDomain or port are not correct! Try Again!\n");
                        return 1;
                    }
                }
            }



            return 0;
        }
    }

    /**
     * Send request via the socket
     * @param message Message to send.
     * @param requestTyp Type of message.
     * @throws IOException if IO error happened
     */





    /**
     * Initializes the login process.
     * @param username Username
     * @param password Password
     * @return True if login was a success, otherwise false.
     */
    public abstract boolean login(String username, String password);

    /**
     * Get password-input based on the type of terminal used to run this application.
     * @return The inputted password.
     */
    private String getPasswordInput(){
        Console console = System.console();
        if(console == null){
            return scanner.nextLine();
        }

        return String.valueOf(console.readPassword());
    }

    public abstract List<String> readMultiLineResponse();



    /**
     * Prompt that handles user/server IO
     * @return True if connection was a success, otherwise false.
     */
    private boolean portHostPrompt(){
        System.out.println("Enter \"quit\" to close app\n");

        while (true){
            System.out.print("Server domain name aka host or IP-Address: ");
            String domain = scanner.nextLine();



            if(domain.equalsIgnoreCase("quit")) return true;

            System.out.print("Port: ");
            String port = scanner.nextLine();

            if(port.equalsIgnoreCase("quit")) return true;
            if(!Utility.isPositiveInteger(port)){
                System.out.println("Port wasn't a valid number!\nTry Again!\n\n");
                continue;
            }



            switch (socketType){
                case 1, 2 -> {

                    try {
                        init(domain, Integer.parseInt(port));

                    } catch (UnknownHostException e){

                        System.out.println("\rHost/IP isn't responding!\nTry Again\n\n");
                        continue;
                    } catch (IllegalArgumentException e){

                        System.out.println("\nPort number is over 65535!\nTry Again");
                        continue;
                    } catch (IOException e){

                        System.out.println("\nLost connection!\nTry Again");
                        continue;
                    }


                    readSingleLineContent();
                }

                case 3 -> {
                    if(this instanceof ClientPoP3) javaMailInstance.initConnectProperties(domain, port, false);
                    else     javaMailInstance.initConnectPropertiesSmtp(domain, port, false);


                }
                case 4 ->{
                    if(this instanceof ClientPoP3) javaMailInstance.initConnectProperties(domain, port, true);
                    else     javaMailInstance.initConnectPropertiesSmtp(domain, port, true);
                }
            }

            return false;
        }
    }

    /**
     * Shuts down client and releases resources.
     */
    public void shutdownClient(){
        try {
            Objects.requireNonNull(bWriter).write("quit");
            bWriter.newLine();
            Objects.requireNonNull(clientSocket).close();
            Objects.requireNonNull(bReader).close();
            Objects.requireNonNull(bWriter).close();

            System.out.println("\rClient shutdown");
        } catch (IOException e) {
            System.out.println("\rLost connection...");
        } catch (NullPointerException ignored){}
    }



    public abstract void viewDashboard();




    public abstract void operationPrompt();


    public abstract boolean socketSelectorPrompt();



    public boolean javaMailSmtpOrPoP3(){

        while (true) {
            System.out.println("Choose type of connection/toApi :\n");
            System.out.println("1. JavaMail API Pop3");
            System.out.println("2. JavaMail API SMTP ");
            System.out.println("3. Exit program");
            System.out.print("\nYour choice: ");

            String choice = scanner.nextLine().trim();
            if(!Utility.checkIntegerInBounds(1, 3, choice)){
                System.out.println("Bad Input! Try Again!\n");
                continue;
            }
            int choiceNumeric = Integer.parseInt(choice);

            if(choiceNumeric == 3) return false;
            JavaMailtyp = choiceNumeric;

            return true;
        }
    }





    private boolean javaMailConnectLoginPrompt(){
        while (true){
            //false if user entered quit
            //it doesn't check connection validation
            if(portHostPrompt()) {
                System.out.println("See ya!");
                return false;
            }

            int loginPromptFlag = loginPrompt();

            switch (loginPromptFlag){
                //Quit state
                case -1 -> {
                    shutdownClient();
                    System.out.println("See ya!");
                    return false;
                }
                //Success
                //Host/port/username/password -> correct input
                case 0 -> {
                    return true;
                }
            }
        }
    }


    /**
     * Starts Client initialization
     */
    public void startClient(){


        if(!socketSelectorPrompt()){
            System.out.println("See ya!");
            return;
        }

        switch (socketType){
            case 1, 2 -> {

                if(portHostPrompt()) {
                    System.out.println("See ya!");
                    return;
                }

                System.out.println("\rConnection established!" + "\s".repeat(25) + "\n");

                if(loginPrompt() == -1){
                    shutdownClient();
                    System.out.println("See ya!");
                    return;


                }
            }

            case 3, 4 -> {
               /* if(!javaMailSmtpOrPoP3()){
                    shutdownClient();
                    return;
                }*/


                if(!javaMailConnectLoginPrompt()){
                    shutdownClient();
                    return;
                }
            }
        }









        System.out.println("\n\nConnected as " + username + ":");
        System.out.println("********************************");
        viewDashboard();

    }
}
