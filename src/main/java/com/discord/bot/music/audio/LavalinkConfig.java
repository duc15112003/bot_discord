package com.discord.bot.music.audio;

/**
 * LavalinkConfig has been merged into JdaConfig to support multi-bot instances.
 * Each bot instance gets its own LavalinkClient, created during JDA
 * initialization.
 *
 * @see com.discord.bot.config.JdaConfig
 * @deprecated Replaced by JdaConfig.createLavalinkClient()
 */
// This class is intentionally left empty — all Lavalink initialization
// is now handled in JdaConfig to support multi-bot architecture.
