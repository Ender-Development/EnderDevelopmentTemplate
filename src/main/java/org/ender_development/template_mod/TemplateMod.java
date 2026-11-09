/*
 * Copyright (c) 2026 Ender-Development.org
 * SPDX-License-Identifier: LGPL-3.0
 */

package org.ender_development.template_mod;

import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// @Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION)
public class TemplateMod {
    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    //    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info(Tags.MOD_NAME);
    }
}
