package net.menoni.pil.bot.discord;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "discord")
@Getter
@Setter
public class DiscordBotConfig {

    private String token;
    private String guildId;
    private String adminChannelId;
    private Boolean forceUpdateCommands = false;
    private String customBotStatus;
    private String limitInteractionsToMember;
//    private String memberRoleId; // disabled
    private String playerRoleId;
    private String teamLeadRoleId;
    private String teamsChannelId;
    private String adminRoleId;
    private String staffRoleId;
    private String casterRoleId;
    private String cmdChannelId;
    private String botLogsChannelId;

    private String matchesCategoryId1;
    private String matchesCategoryId2;
    private String matchesCategoryId3;
    private String matchesCategoryId4;
    private String matchesCategoryId5;
    private String matchesCategoryId6;
    private String matchesCategoryId7;
    private String matchesCategoryId8;
    private String matchesCategoryId9;

    public String getMatchesCategoryId(int division) {
        return switch (division) {
            case 1 -> matchesCategoryId1;
            case 2 -> matchesCategoryId2;
            case 3 -> matchesCategoryId3;
            case 4 -> matchesCategoryId4;
            case 5 -> matchesCategoryId5;
            case 6 -> matchesCategoryId6;
            case 7 -> matchesCategoryId7;
            case 8 -> matchesCategoryId8;
            case 9 -> matchesCategoryId9;
            default -> null;
        };
    }

}
