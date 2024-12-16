package net.menoni.pil.bot.discord;

import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.menoni.pil.bot.jdbc.model.JdbcMember;
import net.menoni.pil.bot.service.MemberService;
import net.menoni.pil.bot.service.TeamService;
import net.menoni.spring.commons.util.LoggerTextFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class DiscordBot {
    private final Logger logger = LoggerFactory.getLogger(DiscordBot.class);

    private static final List<Permission> PERMISSIONS = List.of(
            Permission.MANAGE_ROLES,
            Permission.MANAGE_CHANNEL,
            Permission.MESSAGE_MANAGE
    );
    @Getter
    private final DiscordBotConfig config;
    private final JDA jda;
    private final String joinLink;
    private final String pilBotVersion;
    private final AutowireCapableBeanFactory autowireCapableBeanFactory;

    public DiscordBot(
            DiscordBotConfig config,
            AutowireCapableBeanFactory autowireCapableBeanFactory,
            String pilBotVersion
    ) throws InterruptedException {
        this.config = config;
        this.autowireCapableBeanFactory = autowireCapableBeanFactory;
        this.pilBotVersion = pilBotVersion;
        if (!(this instanceof DiscordBotTest)) {
            this.jda = JDABuilder.create(
                            config.getToken(),
                            List.of(
                                    GatewayIntent.GUILD_MEMBERS,
                                    GatewayIntent.GUILD_MESSAGES,
                                    GatewayIntent.MESSAGE_CONTENT,
                                    GatewayIntent.GUILD_EMOJIS_AND_STICKERS
                            )
                    ).disableCache(
                            CacheFlag.VOICE_STATE,
                            CacheFlag.STICKER,
                            CacheFlag.CLIENT_STATUS,
                            CacheFlag.SCHEDULED_EVENTS,
                            CacheFlag.ACTIVITY,
                            CacheFlag.ONLINE_STATUS
                    )
                    .enableCache(
                            CacheFlag.EMOJI
                    )
                    .setStatus(factorStatus(config))
                    .setActivity(factorActivity(config))
                    .build();
            this.jda.setRequiredScopes(List.of("applications.commands"));
            this.jda.awaitReady();
            // app info
            ApplicationInfo info = this.jda.retrieveApplicationInfo().complete();
            this.joinLink = info.getInviteUrl(this.config.getGuildId(), PERMISSIONS);
        } else {
            this.jda = null;
            this.joinLink = null;
        }
        this.onReady();
    }

    private OnlineStatus factorStatus(DiscordBotConfig config) {
        if (config.getCustomBotStatus() == null || config.getCustomBotStatus().isEmpty()) {
            return OnlineStatus.ONLINE;
        }
        return OnlineStatus.DO_NOT_DISTURB;
    }

    private Activity factorActivity(DiscordBotConfig config) {
        if (config.getCustomBotStatus() == null || config.getCustomBotStatus().isEmpty()) {
            return Activity.playing("pil-bot (" + this.pilBotVersion + ")");
        } else {
            return Activity.playing(this.config.getCustomBotStatus());
        }
    }
    public void autowire(Object object) {
        this.autowireCapableBeanFactory.autowireBean(object);
    }

    public boolean isForceUpdateCommands() {
        return this.config.getForceUpdateCommands();
    }

    public Role getRoleById(String id) {
        if (id == null) {
            return null;
        }
        return applyGuild(g -> g.getRoleById(id), null);
    }

    public TextChannel getTextChannelById(String id) {
        if (id == null) {
            return null;
        }
        return applyGuild(g -> g.getTextChannelById(id), null);
    }

    @Deprecated
    public Role getMemberRole() {
        return null;
//        return getRoleById(this.config.getMemberRoleId());
    }

    public Role getPlayerRole() {
        return getRoleById(this.config.getPlayerRoleId());
    }

    public Role getTeamLeadRole() {
        return getRoleById(this.config.getTeamLeadRoleId());
    }

    public TextChannel getTeamsChannel() {
        return getTextChannelById(this.config.getTeamsChannelId());
    }

    public TextChannel getBotLogsChannel() {
        return getTextChannelById(this.config.getBotLogsChannelId());
    }

    private void onReady() {
        logger.info("Discord join link: {}", joinLink);
        new Thread(() -> {
	        try {
		        Thread.sleep(1000L);
                MemberService memberService = autowireCapableBeanFactory.getBean(MemberService.class);
                this.withGuild(g -> g.loadMembers().onSuccess(members -> {
                    Role memberRole = getMemberRole();
                    Role playerRole = getPlayerRole();
                    Role teamLeadRole = getTeamLeadRole();
                    for (Member member : members) {
                        JdbcMember foundMember = memberService.getOrCreateMember(member);
                        if (foundMember != null) {
                            this.ensurePlayerRole(member, foundMember, memberRole, playerRole, teamLeadRole);
                        }
                    }
                    List<JdbcMember> botMembers = memberService.getAll();
                    for (JdbcMember botMember : botMembers) {
                        String botMemberDiscordId = botMember.getDiscordId();
                        Member foundMember = members.stream().filter(m -> Objects.equals(m.getId(), botMemberDiscordId)).findAny().orElse(null);
                        if (foundMember == null) {
                            logger.warn("Removing member (leave/kick/ban): {}/{}", botMember.getDiscordName(), botMember.getDiscordNick());
                            memberService.deleteMember(botMemberDiscordId);
                        }
                    }
                }));
	        } catch (InterruptedException e) {
                logger.error("Await failed", e);
	        }
        }).start();
    }

    public void ensurePlayerRole(Member discordMember, JdbcMember botMember, Role memberRole, Role playerRole, Role teamLeadRole) {
        autowireCapableBeanFactory.getBean(TeamService.class).ensurePlayerRoles(discordMember, botMember, memberRole, playerRole, teamLeadRole);
    }

    public void withGuild(Consumer<Guild> consumer) {
        Guild guild = this.jda.getGuildById(this.config.getGuildId());
        if (guild != null) {
            consumer.accept(guild);
        }
    }

    public <T> T applyGuild(Function<Guild, T> function, T fallback) {
        Guild guild = this.jda.getGuildById(this.config.getGuildId());
        if (guild != null) {
            return function.apply(guild);
        }
        return fallback;
    }

    public void addEventListener(Object... objects) {
        this.jda.addEventListener(objects);
    }

    public void logAdminChannel(String text, Object... args) {
        this.withGuild(g -> {
            String txt = LoggerTextFormat.fillArgs(text, args);
            if (this.config.getAdminChannelId() != null) {
                TextChannel tc = g.getTextChannelById(this.config.getAdminChannelId());
                if (tc != null) {
                    tc.sendMessage(txt).queue();
                    return;
                }
            }

            logger.warn("[missing admin channel] {}", txt);
        });
    }

    public Role getBotRole() {
        return this.applyGuild(Guild::getBotRole, null);
    }
}
