/*
 * Copyright (c) 2026 Ender-Development.org
 * SPDX-License-Identifier: LGPL-3.0
 */

package org.ender_development.template_mod

import net.minecraft.creativetab.CreativeTabs
import net.minecraftforge.fml.common.Mod
import org.ender_development.catalyx.api.v1.ICatalyxMod

@Mod(
    modid = Reference.MOD_ID,
    name = Reference.MOD_NAME,
    version = Reference.VERSION,
    dependencies = ICatalyxMod.CATALYX_ADDON,
    modLanguageAdapter = ICatalyxMod.MOD_LANGUAGE_ADAPTER,
)
object TemplateModKotlin : ICatalyxMod {
    override val creativeTab: CreativeTabs = CreativeTabs.MISC
}
