package client;

import util.Utility;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.io.*;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class ClientPoP3 extends Client {

    JavaMail javaMailInstance;


    public ClientPoP3() {
        javaMailInstance = JavaMail.getInstance();
    }

    public boolean isOkToken(String response) {
        String token1 = response.split("\\s+")[0];
        if (token1.equalsIgnoreCase("+OK")) return true;
        if (token1.equalsIgnoreCase("-ERR")) return false;

        throw new IllegalResponseException("Server delivered unexpected response! Closing connection...");
    }

    public void sendRequest(String message, RequestTyp requestTyp) throws IOException {
        String line = requestTyp.toString() + " " + message;
        bWriter.write(line.trim());
        bWriter.newLine();
        bWriter.flush();
    }



    public int[] getStat(){
        int[] arr = null;

        switch (socketType){
            case 1, 2 -> {
                try {
                    sendRequest("", RequestTyp.STAT);
                } catch (IOException e) {
                    System.out.println("Error while sending count-request to server!");
                    return null;
                }

                AbstractMap.SimpleEntry<Boolean, String> response = readSingleLineContent();
                assert response != null;
                if(!response.getKey()){
                    System.out.println("Error received from server!");
                    return null;
                }

                String[] tokens = response.getValue().split("\\s+");

                arr = new int[]{Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1])};
            }

            case 3, 4 -> {
                try {

                    javaMailInstance.updateMessages();
                    List<Message> messages = javaMailInstance.getMessages();
                    arr = new int[2];
                    arr[0] = messages.size();


                    arr[1] = javaMailInstance.getEmailInbox().getSize();
                } catch (MessagingException e) {
                    return null;
                }
            }
        }



        return arr;
    }


    public List<String> getListOfEmails(){
        List<String> list = null;

        switch (socketType){
            case 1, 2 -> {
                try {
                    sendRequest("", RequestTyp.LIST);
                } catch (IOException e) {
                    System.out.println("Error while sending count-request to server!");
                    return null;
                }
                list = readMultiLineResponse();
            }

            case 3, 4 -> {
                try {
                    javaMailInstance.updateMessages();

                    list = new ArrayList<>();
                    List<Message> messages = javaMailInstance.getMessages();
                    int[] sizes = javaMailInstance.getEmailInbox().getSizes();

                    for (int i = 0; i < messages.size(); i++)
                        list.add(messages.get(i).getMessageNumber() + " " + sizes[i]);

                } catch (MessagingException e) {
                    return null;
                }
            }
        }


        return list;
    }


    public AbstractMap.SimpleEntry<Boolean, String> readSingleLineContent() {
        try {
            String response = singleLineResponse();
            String[] tokens = response.split("\\s+");
            return new AbstractMap.SimpleEntry<>(isOkToken(response), Utility.concatinateStringFromArray(1, tokens.length, tokens));
        } catch (IOException e) {
            shutdownClient();
        }
        return null;
    }


    public boolean login(String username, String password) {
        try {

            sendRequest(username, RequestTyp.USER);
            if (!Objects.requireNonNull(readSingleLineContent()).getKey()) return false;
            sendRequest(password, RequestTyp.PASS);
            if (!Objects.requireNonNull(readSingleLineContent()).getKey()) return false;


        } catch (IOException e) {

            System.out.println("Error while sending the username!\nTry Again");
            return false;
        }

        return true;
    }


    public String singleLineResponse() throws IOException {
        return bReader.readLine();
    }


    public List<String> readMultiLineResponse() {
        AbstractMap.SimpleEntry<Boolean, String> firstResponse = readSingleLineContent();

        assert firstResponse != null;
        if (!firstResponse.getKey()) return null;

        char terminatingChar = 0x2E;

        try {
            List<String> items = new ArrayList<>();
            String line;
            while (!(line = singleLineResponse()).equalsIgnoreCase(String.valueOf(terminatingChar))) {
                items.add(line.trim());
            }

            return items;
        } catch (IOException e) {
            System.out.println("\rError fetching data!");
        }
        return null;
    }

    @Override
    public void viewDashboard() {

        System.out.println("\r\n1- View message count and size.");
        System.out.println("2- View list of messages.");
        System.out.println("3- View specific message.");
        System.out.println("5- Close connection.");
        System.out.println("6- Show those instructions again.");
        System.out.println("7- Start a Smtp Client");

        operationPrompt();

    }

    @Override
    public void operationPrompt() {
        try {
            System.out.print("\r\n\nYour Choice: ");
            int choice = Integer.parseInt(scanner.nextLine().trim());


            switch (choice) {
                case 1 -> {

                    int[] tokens = getStat();

                    if (tokens == null) {
                        operationPrompt();
                        return;
                    }

                    System.out.println("\rThere are " + tokens[0] + " message(s)." + " ".repeat(10) + "Size: " +
                            String.format("%.2fMB", ((double) tokens[1] / 1e6)));
                    System.out.println();
                    operationPrompt();
                }
                case 2 -> {

                    List<String> items = getListOfEmails();

                    System.out.print("\r");
                    if (items == null) {
                        operationPrompt();
                        return;
                    }

                    System.out.println("\rMessage-number (Size in KB)\n");
                    items.forEach(message -> {
                        String[] tokens = message.split("\\s+");
                        System.out.println((Integer.parseInt(tokens[0]) - 1) +
                                "\t(" + String.format("%.2fKB", ((double) Integer.parseInt(tokens[1]) / 1e3)) + ")");
                    });
                    operationPrompt();
                }
                case 3 -> {
                    if (!retrieveMessage()) {

                        System.out.println("\rMessage was not found!");
                    }

                    viewDashboard();

                }
                case 5 -> shutdownClient();
                case 6 -> {
                    System.out.println();
                    viewDashboard();
                }case 7 ->{
                    this.shutdownClient();
                    Client cl1 = new SMTPClient();
                    cl1.startClient();
                }
                default -> {
                    System.out.println("\rNumber is out of range of choices!\nTry Again");
                    viewDashboard();
                }
            }

        } catch (NumberFormatException e) {
            System.out.println("Numeric input should be entered!\nTry Again");
            viewDashboard();
        } catch (IOException e) {
            System.out.println("Error fetching data from server!\n Try Again");
            viewDashboard();
        }
    }

    @Override
    public boolean socketSelectorPrompt() {

        while (true) {
            System.out.println("Choose type of connection/socket:\n");
            System.out.println("1. Unencrypted Socket");
            System.out.println("2. SSL-Socket");
            System.out.println("3. JavaMail API");
            System.out.println("4. JavaMail API secured");
            System.out.println("5. Exit program");
            System.out.print("\nYour choice: ");

            String choice = scanner.nextLine().trim();
            if(!Utility.checkIntegerInBounds(1, 5, choice)){
                System.out.println("Bad Input! Try Again!\n");
                continue;
            }
            int choiceNumeric = Integer.parseInt(choice);
            if(choiceNumeric == 5) return false;

            socketType = choiceNumeric;

            return true;
        }
    }

    public boolean retrieveMessage() throws IOException {
        while(true){
            System.out.print("Message number: ");
            String num = scanner.nextLine().trim();
            if(!Utility.isPositiveInteger(num)){
                System.out.println("Not a positive integer! Try Again!\n");
                continue;
            }



            switch (socketType){
                case 1, 2 -> {
                    sendRequest(num, RequestTyp.RETR);
                    List<String> messageLines = readMultiLineResponse();
                    if(messageLines == null){

                        System.out.println("\rMessage number is out of bounds!");
                        return false;
                    }


                    System.out.print("\r");
                    messageLines.forEach(System.out::println);
                }

                case 3, 4 -> {
                    try {
                        javaMailInstance.updateMessages();
                        List<Message> messages = javaMailInstance.getMessages();

                        System.out.print("\r\n");
                        System.out.println(javaMailInstance.readMessage(messages.get(Integer.parseInt(num))));
                    } catch (MessagingException | ArrayIndexOutOfBoundsException e) {
                        return false;
                    }
                }
            }


            return true;
        }
    }
}



