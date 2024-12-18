package net.menoni.pil.bot.discord;

import lombok.Getter;
import lombok.Setter;
import net.menoni.jda.commons.discord.AbstractDiscordBotConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "discord")
@Getter
@Setter
public class DiscordBotConfig extends AbstractDiscordBotConfig {

    private String adminChannelId;
    private String playerRoleId;
    private String teamLeadRoleId;
    private String teamsChannelId;
    private String adminRoleId;
    private String staffRoleId;
    private String casterRoleId;
    private String cmdChannelId;
    private String botLogsChannelId;
    private String botHoistDividerRoleId;

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

    private String teamCaptainDivRole1;
    private String teamCaptainDivRole2;
    private String teamCaptainDivRole3;
    private String teamCaptainDivRole4;
    private String teamCaptainDivRole5;
    private String teamCaptainDivRole6;
    private String teamCaptainDivRole7;
    private String teamCaptainDivRole8;
    private String teamCaptainDivRole9;

    public String getTeamCaptainDivRole(int division) {
        return switch (division) {
            case 1 -> teamCaptainDivRole1;
            case 2 -> teamCaptainDivRole2;
            case 3 -> teamCaptainDivRole3;
            case 4 -> teamCaptainDivRole4;
            case 5 -> teamCaptainDivRole5;
            case 6 -> teamCaptainDivRole6;
            case 7 -> teamCaptainDivRole7;
            case 8 -> teamCaptainDivRole8;
            case 9 -> teamCaptainDivRole9;
            default -> null;
        };
    }

    private String teamMemberDivRole1;
    private String teamMemberDivRole2;
    private String teamMemberDivRole3;
    private String teamMemberDivRole4;
    private String teamMemberDivRole5;
    private String teamMemberDivRole6;
    private String teamMemberDivRole7;
    private String teamMemberDivRole8;
    private String teamMemberDivRole9;

    public String getTeamMemberDivRole(int division) {
        return switch (division) {
            case 1 -> teamMemberDivRole1;
            case 2 -> teamMemberDivRole2;
            case 3 -> teamMemberDivRole3;
            case 4 -> teamMemberDivRole4;
            case 5 -> teamMemberDivRole5;
            case 6 -> teamMemberDivRole6;
            case 7 -> teamMemberDivRole7;
            case 8 -> teamMemberDivRole8;
            case 9 -> teamMemberDivRole9;
            default -> null;
        };
    }

}
