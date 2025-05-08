package es.boffmedia.teras.util.objects._old.chatapp;

import java.util.List;

public class CallData {
    private List<User> users;
    private String caller;
    private String callId;

    public CallData(List<User> users, String caller, String callId) {
        this.users = users;
        this.caller = caller;
        this.callId = callId;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public String getCaller() {
        return caller;
    }

    public void setCaller(String caller) {
        this.caller = caller;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public class User {
        private String uuid;
        private boolean active;

        public User(String uuid, boolean active) {
            this.uuid = uuid;
            this.active = active;
        }

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}