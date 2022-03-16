package com.rtm516.mcxboxbroadcast.boostrap.standalone;

import com.rtm516.mcxboxbroadcast.core.SessionInfo;
import com.rtm516.mcxboxbroadcast.core.SessionManager;

public class Main {
    public static void main(String[] args) throws Exception {
        SessionManager flow = new SessionManager("./cache"); // "00000000441cc96b", "http://xboxlive.com"

        SessionInfo sessionInfo = new SessionInfo();
        sessionInfo.setHostName("Geyser Test Server");
        sessionInfo.setWorldName("Test");
        sessionInfo.setVersion("1.18.12");
        sessionInfo.setProtocol(486);
        sessionInfo.setPlayers(0);
        sessionInfo.setMaxPlayers(20);
        sessionInfo.setIp("51.210.124.95");
        sessionInfo.setPort(19132);

        flow.createSession(sessionInfo);

        System.out.println("Created session!");
    }
}
