package net.menoni.pil.bot.discord;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.menoni.commons.util.LoggerTextFormat;
import net.menoni.jda.commons.discord.AbstractDiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcMember;
import net.menoni.pil.bot.service.MemberService;
import net.menoni.pil.bot.service.TeamService;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
public class DiscordBot extends AbstractDiscordBot<DiscordBotConfig> {


    private static final Set<Permission> PERMISSIONS = Set.of(
            Permission.MANAGE_ROLES,
            Permission.MANAGE_CHANNEL,
            Permission.MESSAGE_MANAGE
    );
    private static final List<GatewayIntent> INTENTS = List.of(
            GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.MESSAGE_CONTENT,
            GatewayIntent.GUILD_EXPRESSIONS
    );
    private static final List<CacheFlag> ENABLED_CACHES = List.of(
            CacheFlag.EMOJI
    );
    private static final List<CacheFlag> DISABLED_CACHES = List.of(
            CacheFlag.VOICE_STATE,
            CacheFlag.STICKER,
            CacheFlag.CLIENT_STATUS,
            CacheFlag.SCHEDULED_EVENTS,
            CacheFlag.ACTIVITY,
            CacheFlag.ONLINE_STATUS
    );
    private static final List<String> REQUIRED_SCOPES = List.of("applications.commands");

    public DiscordBot(
            DiscordBotConfig config,
            AutowireCapableBeanFactory autowireCapableBeanFactory
    ) throws InterruptedException {
        super(
                "pil-bot",
                config,
                autowireCapableBeanFactory,
                PERMISSIONS,
                INTENTS,
                ENABLED_CACHES,
                DISABLED_CACHES,
                REQUIRED_SCOPES
        );
    }

    public Role getPlayerRole() {
        return getRoleById(this.getConfig().getPlayerRoleId());
    }

    public Role getTeamLeadRole() {
        return getRoleById(this.getConfig().getTeamLeadRoleId());
    }

    public TextChannel getTeamsChannel() {
        return getTextChannelById(this.getConfig().getTeamsChannelId());
    }

    public TextChannel getBotLogsChannel() {
        return getTextChannelById(this.getConfig().getBotLogsChannelId());
    }

    @Override
    protected void onReady() {
        new Thread(() -> {
	        try {
		        Thread.sleep(1000L);
                MemberService memberService = getAutowireCapableBeanFactory().getBean(MemberService.class);
                this.withGuild(g -> g.loadMembers().onSuccess(members -> {
                    Role playerRole = getPlayerRole();
                    Role teamLeadRole = getTeamLeadRole();
                    for (Member member : members) {
                        JdbcMember foundMember = memberService.getOrCreateMember(member);
                        if (foundMember != null) {
                            this.ensurePlayerRole(member, foundMember, playerRole, teamLeadRole);
                        }
                    }
                    List<JdbcMember> botMembers = memberService.getAll();
                    for (JdbcMember botMember : botMembers) {
                        String botMemberDiscordId = botMember.getDiscordId();
                        Member foundMember = members.stream().filter(m -> Objects.equals(m.getId(), botMemberDiscordId)).findAny().orElse(null);
                        if (foundMember == null) {
                            log.warn("Removing member (leave/kick/ban): {}/{}", botMember.getDiscordName(), botMember.getDiscordNick());
                            memberService.deleteMember(botMemberDiscordId);
                        }
                    }
                }));
	        } catch (InterruptedException e) {
                log.error("Await failed", e);
	        }
        }).start();
    }

    public void ensurePlayerRole(Member discordMember, JdbcMember botMember, Role playerRole, Role teamLeadRole) {
        getAutowireCapableBeanFactory().getBean(TeamService.class).ensurePlayerRoles(discordMember, botMember, playerRole, teamLeadRole);
    }

    public void logAdminChannel(String text, Object... args) {
        this.withGuild(g -> {
            String txt = LoggerTextFormat.fillArgs(text, args);
            if (this.getConfig().getAdminChannelId() != null) {
                TextChannel tc = g.getTextChannelById(this.getConfig().getAdminChannelId());
                if (tc != null) {
                    tc.sendMessage(txt).queue();
                    return;
                }
            }

            log.warn("[missing admin channel] {}", txt);
        });
    }
}
