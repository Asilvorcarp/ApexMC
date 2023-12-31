package com.asilvorcarp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import static com.asilvorcarp.NetworkingConstants.PING_PACKET;
import static com.asilvorcarp.NetworkingConstants.REMOVE_PING_PACKET;

public class ApexMC implements ModInitializer {
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final String MOD_ID = "apex_mc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ArrayList<ApexTeam> teams = new ArrayList<>();
    public static boolean ENABLE_TEAMS = false;

    public static String[] newSounds = {
            "apex_mc:ping_location",
            "apex_mc:ping_item",
            "apex_mc:ping_enemy",
            "apex_mc:mozambique_lifeline",
    };
    // currently include newSounds and SoundEvents.BLOCK_ANVIL_BREAK
    public static ArrayList<SoundEvent> soundEventsForPing;

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        LOGGER.info("Make MC Apex Again!");

        // TODO (later) add team command, save state to file

        // register receiver
        ServerPlayNetworking.registerGlobalReceiver(NetworkingConstants.PING_PACKET, ((server, player, handler, buf, responseSender) -> {
            multicastPing(player, PING_PACKET, buf);
        }));
        ServerPlayNetworking.registerGlobalReceiver(NetworkingConstants.REMOVE_PING_PACKET, ((server, player, handler, buf, responseSender) -> {
            multicastRemovePing(player, REMOVE_PING_PACKET, buf);
        }));

        // register all new sound events
        soundEventsForPing = new ArrayList<>();
        Arrays.stream(newSounds).forEach((soundStr) -> {
            Identifier soundId = new Identifier(soundStr);
            SoundEvent soundEvent = new SoundEvent(soundId);
            Registry.register(Registry.SOUND_EVENT, soundId, soundEvent);
            soundEventsForPing.add(soundEvent);
        });
        soundEventsForPing.add(SoundEvents.BLOCK_ANVIL_BREAK);
    }

    public static void multicastPing(ServerPlayerEntity sender, Identifier channelName, PacketByteBuf buf) {
        if (ENABLE_TEAMS) {
            // TODO implement teams
        } else {
            SoundEvent soundEvent;
            try {
                var p = PingPoint.fromPacketByteBuf(buf);
                soundEvent = soundIdxToEvent(p.sound);
            } catch (Exception e) {
                LOGGER.error("server fail to deserialize the ping packet", e);
                return;
            }
            for (ServerPlayerEntity teammate : PlayerLookup.world((ServerWorld) sender.getWorld())) {
                var senderName = sender.getEntityName();
                var teammateName = teammate.getEntityName();
                // play sound for all // TODO how to play for only one
                teammate.getWorld().playSound(
                        null, // Player - if non-null, will play sound for every nearby player *except* the specified player
                        teammate.getBlockPos(), // The position of where the sound will come from
                        soundEvent,
                        SoundCategory.BLOCKS, // This determines which of the volume sliders affect this sound
                        1f, // Volume multiplier, 1 is normal, 0.5 is half volume, etc
                        1f // Pitch multiplier, 1 is normal, 0.5 is half pitch, etc
                );
                // packet skip oneself
                if (Objects.equals(teammateName, senderName)) {
                    continue;
                }
                var bufNew = PacketByteBufs.copy(buf.asByteBuf());
                ServerPlayNetworking.send(teammate, channelName, bufNew);
                LOGGER.info("%s send ping to %s".formatted(senderName, teammateName));
            }
        }
    }

    private static SoundEvent soundIdxToEvent(byte soundIdx) {
        // if out of range, just return the first one
        try {
            return soundEventsForPing.get(soundIdx);
        } catch (IndexOutOfBoundsException e) {
            return soundEventsForPing.get(0);
        }
    }

    public static void multicastRemovePing(ServerPlayerEntity sender, Identifier channelName, PacketByteBuf buf) {
        if (ENABLE_TEAMS) {
            // TODO implement teams
        } else {
            for (ServerPlayerEntity teammate : PlayerLookup.world((ServerWorld) sender.getWorld())) {
                var senderName = sender.getEntityName();
                var teammateName = teammate.getEntityName();
                // packet skip oneself
                if (Objects.equals(teammateName, senderName)) {
                    continue;
                }
                var bufNew = PacketByteBufs.copy(buf.asByteBuf());
                ServerPlayNetworking.send(teammate, channelName, bufNew);
                LOGGER.info("%s send remove ping to %s".formatted(senderName, teammateName));
            }
        }
    }

    @NotNull
    static Vector3d Vec3dToVector3d(Vec3d cameraDir) {
        Vector3d ret = new Vector3d();
        ret.x = cameraDir.x;
        ret.y = cameraDir.y;
        ret.z = cameraDir.z;
        return ret;
    }

    public static Vector3f Vec3dToV3f(Vec3d v) {
        var ret = new Vector3f();
        ret.y = (float) v.y;
        ret.z = (float) v.z;
        ret.x = (float) v.x;
        return ret;
    }
}