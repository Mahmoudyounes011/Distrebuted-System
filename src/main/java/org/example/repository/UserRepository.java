// repository/UserRepository.java
package org.example.repository;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.example.model.User;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class UserRepository {
    private Map<String, User> usersByToken = new HashMap<>();
    private final String FILE_PATH = "src/data/users.json";
    private final Gson gson = new Gson();

    public UserRepository() {
        loadUsersFromFile();
    }

    public void addUser(User user) {
        usersByToken.put(user.getToken(), user);
        saveAllUsersToFile();
    }

    public User getUserByToken(String token) {
        return usersByToken.get(token);
    }

    public Map<String, User> getAllUsers() {
        return usersByToken;
    }

    private void saveAllUsersToFile() {
        try (Writer writer = new FileWriter(FILE_PATH)) {
            gson.toJson(usersByToken, writer);
        } catch (IOException e) {
            System.out.println("Error writing to JSON file: " + e.getMessage());
        }
    }

    private void loadUsersFromFile() {
        File file = new File(FILE_PATH);
        if (!file.exists()){
            System.out.println("file doesnot exist : " + FILE_PATH);

            return;
        }

        try (Reader reader = new FileReader(FILE_PATH)) {
            Type type = new TypeToken<Map<String, User>>() {}.getType();
            Map<String, User> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                usersByToken = loaded;
            }
        } catch (IOException e) {
            System.out.println("Error reading from JSON file: " + e.getMessage());
        }
    }


}
