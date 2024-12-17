package net.menoni.pil.bot.discord.listener;

import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcMember;
import net.menoni.pil.bot.service.MemberService;
import net.menoni.pil.bot.service.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DiscordMemberListener implements EventListener {

    private static final Logger logger = LoggerFactory.getLogger(DiscordMemberListener.class);

    @Autowired
    private MemberService memberService;

    @Autowired
    private DiscordBot bot;

    @Autowired
    private TeamService teamService;

    @PostConstruct
    public void init() {
        this.bot.addEventListener(this);
    }

    @Override
    public void onEvent(GenericEvent event) {
        if (event instanceof GuildMemberRemoveEvent memberRemoveEvent) {
            this.memberLeaveEvent(memberRemoveEvent);
        } else if (event instanceof GuildMemberJoinEvent memberJoinEvent) {
            this.memberJoinEvent(memberJoinEvent);
        }
    }

    private void memberJoinEvent(GuildMemberJoinEvent event) {
        if (event.getUser().isBot() || event.getUser().isSystem()) {
            return;
        }
        logger.info("Creating new member (join): {}/{}", event.getUser().getId(), event.getUser().getName());
        JdbcMember jdbcMember = memberService.getOrCreateMember(event.getMember());
        Role playerRole = bot.getPlayerRole();
        Role teamLeadRole = bot.getTeamLeadRole();
        teamService.ensurePlayerRoles(event.getMember(), jdbcMember, playerRole, teamLeadRole);
    }

    private void memberLeaveEvent(GuildMemberRemoveEvent event) {
        logger.info("Deleting member (leave/kick/ban): {}/{}", event.getUser().getId(), event.getUser().getName());
        memberService.deleteMember(event.getUser().getId());
    }


}
