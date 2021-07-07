package fr.i360matt.runnableOverNetwork.auth;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Auth that allow to register different auths and perms for each username
 */
public class MultiAuth extends AbstractAuth {
    private final HashMap<String, AbstractAuth> auths = new HashMap<>();
    private final HashMap<String, UserPerms> overrides = new HashMap<>();
    private AbstractAuth defaultAuth;
    private UserPerms defaultPerms;

    @Override
    public void authenticateServer(Socket socket, AuthParams authParams) throws IOException, GeneralSecurityException {
        final AbstractAuth auth = this.auths.getOrDefault(authParams.username, this.defaultAuth);
        if (auth == null) {
            socket.close();
        } else {
            auth.authenticateServer(socket, authParams);
            final UserPerms userPerms = this.overrides.getOrDefault(authParams.username, this.defaultPerms);
            if (userPerms != null) {
                authParams.blockedActions.addAll(userPerms.blockedActions);
                if (userPerms.allowRemoteClose != null)
                    authParams.allowRemoteClose = userPerms.allowRemoteClose;
                if (userPerms.allowUnsafeSerialisation != null)
                    authParams.allowUnsafeSerialisation =
                            userPerms.allowUnsafeSerialisation;
            }
        }
    }

    @Override
    public void authenticateClient(Socket socket, AuthParams authParams) throws IOException, GeneralSecurityException {
        final AbstractAuth auth = this.auths.getOrDefault(authParams.username, this.defaultAuth);
        if (auth == null) {
            socket.close();
        } else {
            auth.authenticateClient(socket, authParams);
        }
    }

    public void putUsernameAuth(String username,AbstractAuth auth) {
        this.auths.put(username, auth);
    }

    public void putUsernamePerms(String username,UserPerms perms) {
        this.overrides.put(username, perms);
    }

    public void setDefaultAuth(AbstractAuth defaultAuth) {
        this.defaultAuth = defaultAuth;
    }

    public void setDefaultPerms(UserPerms defaultPerms) {
        this.defaultPerms = defaultPerms;
    }

    public static class UserPerms {
        public final Set<Integer> blockedActions;
        public Boolean allowRemoteClose;
        public Boolean allowUnsafeSerialisation;

        public UserPerms() {
            this.blockedActions = new HashSet<>();
        }
    }
}
