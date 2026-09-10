package dev.xyat.kineticcore.feature.experience.event;

import dev.xyat.kineticcore.KineticCore;
import dev.xyat.kineticcore.feature.mechanics.config.GeneralMechanicsConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = KineticCore.MODID)
public class XPDropHandler {
    private static final String DEATH_RECOVERY_XP_TAG = "kineticcore:death_recovery_xp";

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        ServerLevel level = (ServerLevel) player.level();

        // 1. 检查是否开启了 keepInventory。只有开启时，本功能才干预经验掉落
        boolean keepInventory = level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        if (!keepInventory) return;

        // 2. 检查配置：如果掉落比例 <= 0，则完全保留（原版 keepInventory 行为）
        int configPercentage = GeneralMechanicsConfig.keepInvXPDropPercentage;
        if (configPercentage <= 0) return;

        // 3. 检查玩家是否有经验
        if (player.totalExperience <= 0) return;

        // 4. 计算掉落量 (限制在 0% - 100% 之间)
        double percentage = Math.min(100, configPercentage) / 100.0;
        int dropAmount = (int) (player.totalExperience * percentage);

        if (dropAmount > 0) {
            // 5. 在世界中生成经验球
            awardDeathExperience(level, player, dropAmount);

            // 6. 关键修复：扣除玩家身上的经验
            // 直接调用 giveExperiencePoints(-dropAmount) 可能不会正确回退等级。
            // 最安全的方法是计算剩余经验值，然后重置玩家经验并重新赋予剩余部分。
            int remainingXP = Math.max(0, player.totalExperience - dropAmount);

            // 重置玩家经验状态
            player.totalExperience = 0;
            player.experienceLevel = 0;
            player.experienceProgress = 0;

            // 重新赋予剩余经验 (giveExperiencePoints 会自动根据总额计算出正确的等级和进度条)
            if (remainingXP > 0) {
                player.giveExperiencePoints(remainingXP);
            }

            KineticCore.LOGGER.debug("Player {} died with keepInventory. Dropped {} XP ({}%), remaining {} XP.",
                    player.getName().getString(), dropAmount, configPercentage, remainingXP);
        }
    }

    private static void awardDeathExperience(ServerLevel level, Player player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int value = ExperienceOrb.getExperienceValue(remaining);
            remaining -= value;

            ExperienceOrb orb = new ExperienceOrb(
                    level,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    value
            );
            orb.getPersistentData().putBoolean(DEATH_RECOVERY_XP_TAG, true);
            level.addFreshEntity(orb);
        }
    }

}
