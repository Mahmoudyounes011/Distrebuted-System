// model/User.java
package org.example.model;

public class User {
    private String username;
    private String department;
    private String role; // "Manager" or "Employee"
    private String token;

    public User(String username, String department, String role, String token) {
        this.username = username;
        this.department = department;
        this.role = role;
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public String getDepartment() {
        return department;
    }

    public String getRole() {
        return role;
    }

    public String getToken() {
        return token;
    }

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", department='" + department + '\'' +
                ", role='" + role + '\'' +

                '}';
    }
}
