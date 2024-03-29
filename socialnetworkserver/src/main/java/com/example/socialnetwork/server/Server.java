package com.example.socialnetwork.server;

import com.example.socialnetwork.domain.*;
import com.example.socialnetwork.repository.FriendshipDBRepository;
import com.example.socialnetwork.repository.MessageDBRepository;
import com.example.socialnetwork.repository.RequestsDBRepository;
import com.example.socialnetwork.repository.db.UserDBPagingRepository;
import com.example.socialnetwork.repository.paging.PageableImplementation;
import com.example.socialnetwork.validators.FriendshipValidator;
import com.example.socialnetwork.validators.UtilizatorValidator;
import com.example.socialnetwork.validators.Validator;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server implements Runnable{
    private ArrayList<ConnectionHandler> connectionHandlers;
    private ServerSocket server;
    private boolean done;
    private ExecutorService pool;
    private MessageDBRepository repoMessages;
    private RequestsDBRepository repoRequests;
    private UserDBPagingRepository repoUsers;
    private FriendshipDBRepository repoFriendship;

    public Server(MessageDBRepository repoMessages, RequestsDBRepository repoRequests, UserDBPagingRepository repoUsers, FriendshipDBRepository repoFriendship) {
        this.connectionHandlers = new ArrayList<ConnectionHandler>();
        this.done = false;
        this.repoMessages = repoMessages;
        this.repoRequests = repoRequests;
        this.repoUsers = repoUsers;
        this.repoFriendship = repoFriendship;
    }


    @Override
    public void run() {
        try {
            server = new ServerSocket(7999);
            pool = Executors.newCachedThreadPool();
            while (!done) {
                Socket client = server.accept();
                ConnectionHandler connectionHandler = new ConnectionHandler(client);
                connectionHandlers.add(connectionHandler);
                pool.execute(connectionHandler);
            }
        } catch (IOException e) {
            shutdown();
            System.out.println("Serverul a fost inchis");
        }
    }

    public static void main(String[] args) {
        System.out.println("Server Social Network");
        Validator<Long, User> valUser = new UtilizatorValidator();
        Validator<Tuple<Long, Long>, Friendship> valFriendship = new FriendshipValidator();
        MessageDBRepository repoMessages =new MessageDBRepository("jdbc:postgresql://localhost:5432/socialnetwork",
                "postgres", "XKSH2hztw*RXNK");
        RequestsDBRepository repoRequests = new RequestsDBRepository("jdbc:postgresql://localhost:5432/socialnetwork",
                "postgres", "XKSH2hztw*RXNK");
        UserDBPagingRepository repoUsers = new UserDBPagingRepository("jdbc:postgresql://localhost:5432/socialnetwork",
                "postgres", "XKSH2hztw*RXNK", valUser);
        FriendshipDBRepository repoFriendships =new FriendshipDBRepository("jdbc:postgresql://localhost:5432/socialnetwork",
                "postgres", "XKSH2hztw*RXNK", valFriendship);
        Server server = new Server(repoMessages, repoRequests, repoUsers, repoFriendships);
        server.run();
    }
    private void shutdown() {
        try {
            done = true;
            if (!server.isClosed()) {
                server.close();
            }
            for (ConnectionHandler ch : connectionHandlers)
                ch.shutdown();
        }
        catch (IOException e){
                //ignore
            }
    }
    class ConnectionHandler implements Runnable{
        private Socket client;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        public ConnectionHandler(Socket client){
            this.client = client;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(client.getOutputStream());
                in = new ObjectInputStream(client.getInputStream());

                //Message messageTest = new Message(2L, 4L, "SERVER: Welcome!");
                //out.writeObject(new CommunicationObject(Command.SEND_MESSAGE, messageTest));

                Object receivedObject;
                while ((receivedObject = in.readObject()) != null) {
                    if (receivedObject instanceof CommunicationObject) {
                        CommunicationObject comObj = (CommunicationObject) receivedObject;
                        switch (comObj.getCommand()) {
                            case ADD_MESSAGE: {
                                addMessage((Message)comObj.getObject());
                                break;
                            }
                            case REQUEST_MESSAGES_FOR_FRIENDSHIP: {
                                requestMessagesForFriendship((Friendship) comObj.getObject());
                                break;
                            }
                            case REPLY_MESSAGE: {
                                replyMessage((Reply) comObj.getObject());
                                break;
                            }
                            case REQUEST_MESSAGES: {
                                requestMessages();
                                break;
                            }
                            case REQUEST_MESSAGE: {
                                requestMessage((Long) comObj.getObject());
                                break;
                            }
                            case REQUEST_REQUEST: {
                                requestRequest((Tuple<Long, Long>) comObj.getObject());
                                break;
                            }
                            case REQUEST_REQUESTS: {
                                requestRequests();
                                break;
                            }
                            case SEND_REQUEST: {
                                sendRequest((Request) comObj.getObject());
                                break;
                            }
                            case DELETE_REQUEST: {
                                deleteRequest((Tuple<Long, Long>) comObj.getObject());
                                break;
                            }
                            case ACCEPT_REQUEST: {
                                acceptRequest((Tuple<Long, Long>) comObj.getObject());
                                break;
                            }
                            case DECLINE_REQUEST: {
                                declineRequest((Tuple<Long, Long>) comObj.getObject());
                                break;
                            }
                            case REQUEST_USER: {
                                requestUser((Long)comObj.getObject());
                                break;
                            }
                            case REQUEST_USER_BY_CREDENTIALS: {
                                requestIdUser((User)comObj.getObject());
                                break;
                            }
                            case REQUEST_USERS: {
                                requestUsers();
                                break;
                            }
                            case REQUEST_USERS_PAGE: {
                                requestUsersPage((Integer) comObj.getObject());
                                break;
                            }
                            case ADD_USER: {
                                addUser((User)comObj.getObject());
                                break;
                            }
                            case DELETE_USER: {
                                deleteUser((Long)comObj.getObject());
                                break;
                            }
                            case UPDATE_USER: {
                                updateUser((User)comObj.getObject());
                                break;
                            }
                            case REQUEST_FRIENDSHIP: {
                                requestFriendship((Tuple<Long, Long>) comObj.getObject());
                                break;
                            }
                            case REQUEST_FRIENDSHIPS: {
                                requestFriendships();
                                break;
                            }
                            case ADD_FRIENDSHIP: {
                                addFriendship((Friendship) comObj.getObject());
                                break;
                            }
                            case DELETE_FRIENDSHIP: {
                                deleteFriendship((Tuple<Long, Long>) comObj.getObject());
                                break;
                            }
                        }
                    }
                    else {
                        System.out.println("Mesaj necunoscut!!!");
                    }
                }
            }
            catch (IOException | ClassNotFoundException e) {
                shutdown();
                System.out.println("Serverul a fost inchis");
            }
        }

        private void deleteFriendship(Tuple<Long, Long> object) {
            repoFriendship.delete(object);
        }

        private void addFriendship(Friendship object) {
            repoFriendship.save(object);
        }

        private void requestFriendships() {
            Iterable<Friendship> friendships = repoFriendship.findAll();
            CommunicationObject response = new CommunicationObject(Command.FRIENDSHIPS, friendships);
            try {
                out.writeObject(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void requestFriendship(Tuple<Long, Long> object) {
            Friendship friendship = repoFriendship.findOne(object);
            CommunicationObject response = new CommunicationObject(Command.FRIENDSHIP, friendship);
            try {
                out.writeObject(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void updateUser(User object) {
            repoUsers.update(object);
        }

        private void deleteUser(Long object) {
            repoUsers.delete(object);
        }

        private void addUser(User object) {
            repoUsers.save(object);
        }

        private void requestUsers() {
            Iterable<User> users = repoUsers.findAll();
            CommunicationObject response = new CommunicationObject(Command.USERS, users);
            try {
                out.writeObject(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        private void requestIdUser(User user) {
            Long id = repoUsers.getIdUser(user);
            CommunicationObject response = new CommunicationObject(Command.REQUEST_USER_BY_CREDENTIALS, id);
            try {
                out.writeObject(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        private void requestUsersPage(Integer pageIndex) {
            Iterable<User> users = repoUsers.findAll(new PageableImplementation(pageIndex, 5)).getContent().toList();
            CommunicationObject response = new CommunicationObject(Command.USERS, users);
            try {
                out.writeObject(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void requestUser(Long object) {
            User user = repoUsers.findOne(object);
            CommunicationObject response = new CommunicationObject(Command.USER, user);
            try {
                out.writeObject(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void declineRequest(Tuple<Long, Long> object) {
            repoRequests.declineRequest(object.getLeft(), object.getRight());
        }

        private void acceptRequest(Tuple<Long, Long> object) {
            repoRequests.acceptRequest(object.getLeft(), object.getRight());
        }

        private void deleteRequest(Tuple<Long, Long> object) {
            repoRequests.delete(object);
        }

        private void sendRequest(Request object) {
            repoRequests.save(object);
        }

        private void requestRequests() {
            Iterable<Request> requests = repoRequests.findAll();
            CommunicationObject response = new CommunicationObject(Command.REQUESTS, requests);
            try {
                out.writeObject(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void requestRequest(Tuple<Long, Long> object) {
            Request request = repoRequests.findOne(object);
            CommunicationObject response = new CommunicationObject(Command.REQUEST, request);
            try {
                out.writeObject(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void requestMessage(Long object) {
            Message message = repoMessages.findOne(object);
            CommunicationObject response = new CommunicationObject(Command.MESSAGE, message);
            try {
                out.writeObject(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void requestMessages() {
            Iterable<Message> messages = repoMessages.findAll();
            CommunicationObject response = new CommunicationObject(Command.MESSAGES, messages);
            try {
                out.writeObject(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void replyMessage(Reply reply) {
            repoMessages.replyMessage(reply);
        }


        public void requestMessagesForFriendship (Friendship friendship){
            ArrayList<Message> msgs = repoMessages.getUserMessages(friendship.getId().getLeft(), friendship.getId().getRight());
            CommunicationObject response = new CommunicationObject(Command.MESSAGES_FOR_FRIENDSHIP, msgs);
            try {
                out.writeObject(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        public void addMessage (Message message){
            repoMessages.save(message);
        }












        private void shutdown(){
            try {
                in.close();
                out.close();
                if(!client.isClosed())
                    client.close();
            } catch (IOException e) {
                //ignore
            }
        }
    }
}
