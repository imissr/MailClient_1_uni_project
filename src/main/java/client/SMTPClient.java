package client;

import util.Utility;


import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.util.*;

public class SMTPClient extends Client {

    //    List<Boolean> token;
    public Boolean rcptToType;

    public Set<String> services = new HashSet<>();

    public SMTPClient() {
        javaMailInstance = JavaMail.getInstance();
    }


    private String base64Encoder(String message) {
        return Base64.getEncoder().encodeToString(message.getBytes());
    }

    @Override
    public String singleLineResponse() throws IOException {
        return bReader.readLine();
    }


    /*
    resetsmtp get called in case we have nested email error
    its reset the command to enter mail from again
     */
    public boolean resetSMTP() {
        try {
            sendRequest("", "rset");
            return readSingleLineContent().getKey();

        } catch (IOException e) {
            startClient();
            System.out.println("Error while reseting SMTP");
            return false;
        }
    }

    /*
    startTLS SEND TLS COMMAND TO START IT
     */
    public boolean startTLS() {
        try {
            sendRequest("", "STARTTLS"); //Todo
            return readSingleLineContent().getKey();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("cant start a secure connection with the smtp server");
        }
        return true;
    }

    /*
    its load the services by send EhLO command and save it in services List
     */
    public void loadServices() {
        try {
            sendRequest(InetAddress.getLocalHost().getHostAddress(), "EHLO");
            String response;
            do {
                response = singleLineResponse();
                services.add(response.substring(4));
            } while (response.charAt(3) != ' ');
        } catch (IOException e) {
            System.err.println(e.getLocalizedMessage());
        }
    }

    /*
    to check if the service exist in array
     */
    public boolean hasService(String service) {
        if (services.isEmpty()) return false;
        return services.stream()
                .anyMatch(serv -> serv.equalsIgnoreCase(service));
    }

/*
check if respnse of the server is right
 */
    @Override
    public boolean isOkToken(String response) {
        String token = response.substring(0, 3);
        if (token.equalsIgnoreCase("250") || token.equalsIgnoreCase("220") || token.equalsIgnoreCase("235")
                || token.equalsIgnoreCase("334") || token.equalsIgnoreCase("354"))
            return true;

        if (token.equalsIgnoreCase("550") || token.equalsIgnoreCase("500") || token.equalsIgnoreCase("501")
                || token.equalsIgnoreCase("502") || token.equalsIgnoreCase("504") || token.equalsIgnoreCase("421") ||
                token.equalsIgnoreCase("535") || token.equalsIgnoreCase("503") || token.equalsIgnoreCase("553"))
            return false;

        throw new IllegalResponseException("Server delivered unexpected response! Closing connection...");
    }


    @Override
    public AbstractMap.SimpleEntry<Boolean, String> readSingleLineContent() {
        {
            try {
                String response = singleLineResponse();
                String tokens = response.substring(4);
                return new AbstractMap.SimpleEntry<>(isOkToken(response), tokens);
            } catch (IOException e) {
                shutdownClient();
            }
            return null;
        }
    }


    @Override
    public boolean login(String username, String password) {
        try {
            loadServices();
            sendRequest("AUTH LOGIN", "");
            breaderFlush();
            sendRequest(base64Encoder(username), "");
            if (!readSingleLineContent().getKey()) return false;
            sendRequest(base64Encoder(password), "");
            if (!readSingleLineContent().getKey()) return false;


        } catch (IOException e) {

            System.out.println("Error while sending the username!\nTry Again");
            return false;

        }

        return true;
    }

    @Override
    public List<String> readMultiLineResponse() {

        return null;
    }


    //method to flush the bufferreader
    public void breaderFlush() throws IOException {
        String response;
        do {
            response = singleLineResponse();
        } while (response.charAt(3) != ' ');
    }


    @Override
    public void viewDashboard() {
        switch (socketType) {
            case 1, 2 -> {
                System.out.println("\r\n1- Send an Email.");
                System.out.println("2- send encrypted Email");
                System.out.println("3- Close connection.");
                System.out.println("4- Show those instructions again.");
                System.out.println("5- Start PoP3 client ");
                operationPrompt();

            }
            case 3, 4 -> {
                System.out.println("\r\n1- Send an Email.");
                System.out.println("2- Close connection.");
                System.out.println("3- Show those instructions again.");
                System.out.println("4- Start PoP3 client ");
                operationPrompt();
            }
        }

    }



//method to send mailfrom command  and if it's true -> there is error ; false -> the methode work and calls rcptToPayload() ;
    public boolean mailFromRequest(String email) {
        try {

            sendRequest(email, "MAIL FROM:");
            if (!readSingleLineContent().getKey()) return true;

        } catch (IOException e) {
            System.out.println("Couldn't send email");
            return true;
        } catch (NullPointerException e) {
            System.out.println("Lost connection...");
        }
        rcptToPayload();
        return false;

    }


// list of Recipients and save it in a list
    public List<String> getListOfRecipients() {
        List<String> items = new ArrayList<>();
        String line = "null";
        int i = 1;
        while (!line.equalsIgnoreCase("")) {
            System.out.println("please enter the " + i + ".recipient ");
            line = scanner.nextLine();
            if(line.equalsIgnoreCase("") )  break;
            if (!Utility.emailCheck(line) ) {
                System.out.println("not valid Email-Adress");

            } else {
                items.add(line);
                i++;
            }

        }

        return items;

    }

    // send Recipients to the server and call data method if everything right
    public boolean rcptToPayload() {
        try {
            String rcp = getRecipient();
            if (!rcptToType) {
                if (!Utility.emailCheck(rcp)) {
                    System.out.println("please enter a valid Mail");
                    rcptToPayload();
                }else{
                    sendRequest(rcp, "RCPT TO:");
                    var response = singleLineResponse();

                    if(response.substring(0,3).equalsIgnoreCase("553")){
                        System.out.println("please enter your email right");
                        resetSMTP();
                        return false;
                    }else if (!isOkToken(response)){
                        System.out.println("Sender address rejected");
                        System.out.println("please try again");
                        resetSMTP();

                        return false;


                    }
                }


                data();

            } else {
                List<String> items = getListOfRecipients();
                for (String x : items) {
                    sendRequest(x, "RCPT TO:");
                    if (!readSingleLineContent().getKey()) {
                        System.out.println("Sender address rejected");
                        System.out.println("please try again");
                        resetSMTP();

                        return false;
                    }

                }

                data();
            }

        } catch (IOException e) {
            System.out.println("Coldnt send email");
            return false;
        }
        return true;

    }

// send the data command to the server and subject too and calls method  messageData();
    public boolean data() {
        String subject;
        try {

            sendRequest("", "DATA");
            if (!readSingleLineContent().getKey()) return false;
            subject = getSubject();
            sendRequest(subject, "Subject:");
            messageData();
        } catch (IOException e) {
            System.out.println("Coldnt send Email");
            return false;
        }
        return true;

    }

// its send the actul message to the server
    public boolean messageData() {

        String message;
        try {

            message = getBody();
            sendRequest(message + "\r\n.", "");
            if (!readSingleLineContent().getKey()) {
                resetSMTP();
                return false;
            }
            System.out.println("Email has been send secssufly");


        } catch (IOException e) {
            System.out.println("Coldnt send Email");
            return false;
        }
        return true;

    }

//get multi Recipients and save in address list for javamail API
    public Address[] getMultiableRecpList() {
        List<String> listOfEmails = getListOfRecipients();
        InternetAddress[] address = new InternetAddress[listOfEmails.size()];
        try {


            for (int i = 0; i < listOfEmails.size(); i++) {
                address[i] = new InternetAddress(listOfEmails.get(i));
            }
        }catch (AddressException e) {
        return null;
    }
            return address;

    }

    //send the email with the most important information to multi rcp
    public boolean sendMailJavaApiToMRcp(){
    try{

        String from = getFrom();
        String name = getName();
        Address[] recipient = getMultiableRecpList();
        String subject = getSubject();
        String body = getBody();

        if (!javaMailInstance.sendmail(from, subject, body, name, recipient)) {
            return false;
        }

    } catch ( UnsupportedEncodingException e) {
        return false;
    } catch (MessagingException e) {
        throw new RuntimeException(e);
    }

        return true;
    }


    //send the email with the most important information to one rcp
    public boolean sendMailJavaApi() {
        try {
                String from = getFrom();
                String name = getName();
                String recipient = getRecipient();
                String subject = getSubject();
                String body = getBody();

                if (!javaMailInstance.sendmail(from, subject, body, name, new InternetAddress(recipient))) {
                    return false;


            }


        } catch (MessagingException  e) {
            System.out.println("MessagingException");
            return false;
        }catch(UnsupportedEncodingException e){
            System.out.println("UnsupportedEncodingException");
            return false;
        }

        return true;
    }




//sendmail prompt thats call every method for javamail api and normal socket
    public void sendmailPrompt() {
        try {
            System.out.print("\r\n\nYour Choice: ");
            int choice = Integer.parseInt(scanner.nextLine());
            switch (socketType) {
                case 1 -> {
                    switch (choice) {
                        case 1 -> {
                            rcptToType = false;
                            String email = getFrom();
                            if (mailFromRequest(email)) System.out.println("couldnt send the email adress");
                            if (resetSMTP()) sendMailDashboard();


                        }
                        case 2 -> {
                            rcptToType = true;
                            String email = getFrom();
                            if (mailFromRequest(email)) System.out.println("couldnt send the email ");
                            if (resetSMTP()) sendMailDashboard();

                        }

                        case 3 -> shutdownClient();
                        case 4 -> {
                            System.out.println();
                            sendMailDashboard();

                        }

                        case 5 -> {
                            System.out.println();
                            viewDashboard();

                        }
                        default -> {
                            System.out.println("\rNumber is out of range of choices!\nTry Again");
                            sendMailDashboard();
                        }
                    }
                }
                case 3 , 4 -> {

                    switch (choice) {
                        case 1 -> {
                            if (sendMailJavaApi()) {
                                System.out.println("the message has been sent successfully");
                                sendMailDashboard();
                            } else {
                                System.out.println("problem accourd while sending the email");
                                sendMailDashboard();
                            }


                        }
                        case 2 -> {
                            if (sendMailJavaApiToMRcp()) {
                                System.out.println("the message has been sent successfully");
                                sendMailDashboard();
                            } else {
                                System.out.println("problem accourd while sending the email");
                                sendMailDashboard();
                            }


                        }


                        case 3 -> shutdownClient();


                        case 4 -> {
                            System.out.println();
                            sendMailDashboard();

                        }

                        case 5 -> {
                            System.out.println();
                            viewDashboard();

                        }
                        default -> {
                            System.out.println("\rNumber is out of range of choices!\nTry Again");
                            sendMailDashboard();
                        }
                    }

                }



            }

        } catch (NumberFormatException e) {
            System.out.println("Numeric input should be entered!\nTry Again");
            sendMailDashboard();
        }

    }

    public String getFrom() {

        System.out.println("please enter your email ");
        String from = "";
       do{
           from = scanner.nextLine();

       }while(!Utility.emailCheck(from));


        return from;
    }

    public String getRecipient() {
        System.out.println("please enter rcpto: (enter end to go back to the dashboard) ");
        String rcp = "";
        do{
            rcp = scanner.nextLine();

        }while(!Utility.emailCheck(rcp));


        return rcp;
    }


    public String getSubject() {
        System.out.println("Subject: (enter end to go back to the dashboard)");
        String subject = scanner.nextLine();
        if(subject.equals("end") ) sendMailDashboard();
        return subject;
    }

    public String getName() {
        System.out.println("Please enter the name of the company or your name: (enter stop to go back to the dashboard) ");
        return scanner.nextLine();
    }

    public String getBody() {
        System.out.println("please enter the Email_message: (enter end to go back to the dashboard) ");
        String body = scanner.nextLine();
        if(body.equals("end")) sendMailDashboard();
        return body;
    }


    public void sendMailDashboard() {
        System.out.println("\r\n1- Send an Email to a one recipient");
        System.out.println("2. send an Email to multiple recipients");
        System.out.println("3- Close connection.");
        System.out.println("4- Show those instructions again.");
        System.out.println("5- Go back");
        sendmailPrompt();
    }


    // the operation prompet call the sendmaildashborad which do the first operations by calling sendmailPrompt()
    @Override
    public void operationPrompt() {
        try {
            System.out.print("\r\n\nYour Choice: ");
            int choice = Integer.parseInt(scanner.nextLine().trim());
            switch (socketType) {
                case 1, 2 -> {

                    switch (choice) {
                        case 1 -> {
                            sendMailDashboard();

                        }
                        case 2 -> {
                            if (startTLS() && hasService("STARTTLS")) sendMailDashboard();

                        }

                        case 3 -> shutdownClient();

                        case 4 -> {

                            System.out.println();
                            viewDashboard();

                        }
                        case 5 -> {
                            this.shutdownClient();
                            Client cl = new ClientPoP3();
                            cl.startClient();

                        }
                        default -> {
                            System.out.println("\rNumber is out of range of choices!\nTry Again");
                            viewDashboard();

                        }
                    }
                }
                case 3, 4 -> {
                    if (choice != 1) {
                        choice++;

                    }
                    switch (choice) {
                        case 1 -> {
                            System.out.println("hi");
                            sendMailDashboard();

                        }

                        case 3 -> shutdownClient();

                        case 4 -> {

                            System.out.println();
                            viewDashboard();

                        }
                        case 5 -> {
                            this.shutdownClient();
                            Client cl = new ClientPoP3();
                            cl.startClient();

                        }
                        default -> {
                            System.out.println("\rNumber is out of range of choices!\nTry Again");
                            viewDashboard();

                        }
                    }
                }

            }
        } catch (NumberFormatException e) {
            System.out.println("Numeric input should be entered!\nTry Again");
            viewDashboard();
        }

    }

    //got changed becuase the ssl socket doesnt work on the SMTP protcol

    @Override
    public boolean socketSelectorPrompt() {
        {
            while (true) {
                System.out.println("Choose type of connection/socket:\n");
                System.out.println("1. Unencrypted Socket");
                System.out.println("2. JavaMail API");
                System.out.println("3. JavaMail API secured");
                System.out.println("4. Exit program");
                System.out.print("\nYour choice: ");

                String choice = scanner.nextLine().trim();
                if (!Utility.checkIntegerInBounds(1, 4, choice)) {
                    System.out.println("Bad Input! Try Again!\n");
                    continue;
                }
                int choiceNumeric = Integer.parseInt(choice);
                if (choiceNumeric != 1) {
                    choiceNumeric++;
                }
                if (choiceNumeric == 5) return false;
                socketType = choiceNumeric;

                return true;
            }
        }
    }

    //methode that send command to the server

    public void sendRequest(String message, String requestTyp) throws IOException {
        if (requestTyp.equalsIgnoreCase("")) {
            bWriter.write(message.trim());
            bWriter.newLine();
            bWriter.flush();
        } else {
            String line = requestTyp + " " + message;
            bWriter.write(line.trim());
            bWriter.newLine();
            bWriter.flush();

        }

    }

}
