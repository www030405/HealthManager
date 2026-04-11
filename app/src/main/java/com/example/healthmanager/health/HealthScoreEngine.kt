package com.example.healthmanager.health

/**
 * 多维度健康评分引擎
 *
 * 综合运动、睡眠、饮食三个维度，各维度独立打分（0-100），
 * 加权计算总健康评分：运动 40% + 睡眠 30% + 饮食 30%
 *
 * 评分依据：
 * - 运动维度：步数达标率 + 运动时长 + 运动天数
 * - 睡眠维度：睡眠时长是否在 7-9h 区间 + 睡眠质量评分
 * - 饮食维度：卡路里摄入是否在目标范围 + 是否记录了三餐
 */
object HealthScoreEngine {

    private const val WEIGHT_EXERCISE = 0.40f
    private const val WEIGHT_SLEEP = 0.30f
    private const val WEIGHT_DIET = 0.30f

    /**
     * 计算综合健康评分
     */
    fun calculateTotalScore(
        exerciseScore: Float,
        sleepScore: Float,
        dietScore: Float
    ): Float {
        return (exerciseScore * WEIGHT_EXERCISE +
                sleepScore * WEIGHT_SLEEP +
                dietScore * WEIGHT_DIET).coerceIn(0f, 100f)
    }

    /**
     * 运动维度评分（0-100）
     *
     * 子项权重：
     * - 步数达标率 50%：todaySteps / targetSteps，超过目标也最多100分
     * - 运动时长 30%：当天运动总分钟数，>=60min 满分
     * - 运动频率 20%：近7天有运动记录的天数，>=5天满分
     *
     * @param todaySteps 今日步数（传感器 + 数据库）
     * @param targetSteps 用户设定的目标步数
     * @param todayExerciseMinutes 今日运动总分钟数
     * @param weekActiveDays 近7天有运动记录的天数
     */
    fun calculateExerciseScore(
        todaySteps: Int,
        targetSteps: Int,
        todayExerciseMinutes: Int,
        weekActiveDays: Int
    ): Float {
        val target = if (targetSteps > 0) targetSteps else 8000

        // 步数达标率（0-100）
        val stepRatio = (todaySteps.toFloat() / target).coerceIn(0f, 1.2f)
        val stepScore = (stepRatio * 100f).coerceAtMost(100f)

        // 运动时长（0-100），60分钟满分
        val durationScore = (todayExerciseMinutes.toFloat() / 60f * 100f).coerceIn(0f, 100f)

        // 运动频率（0-100），5天满分
        val frequencyScore = (weekActiveDays.toFloat() / 5f * 100f).coerceIn(0f, 100f)

        return (stepScore * 0.5f + durationScore * 0.3f + frequencyScore * 0.2f)
    }

    /**
     * 睡眠维度评分（0-100）
     *
     * 子项权重：
     * - 睡眠时长 60%：7-9h 区间满分，偏离越多扣分越多
     * - 睡眠质量 40%：用户自评的 1-5 分映射到 0-100
     *
     * @param durationHours 睡眠时长（小时）
     * @param quality 睡眠质量（1-5）
     * @param hasRecord 是否有睡眠记录
     */
    fun calculateSleepScore(
        durationHours: Float,
        quality: Int,
        hasRecord: Boolean
    ): Float {
        if (!hasRecord) return 0f

        // 睡眠时长评分：7-9h 满分，每偏离 1h 扣 20 分
        val durationScore = when {
            durationHours in 7.0f..9.0f -> 100f
            durationHours in 6.0f..7.0f -> 80f
            durationHours in 9.0f..10.0f -> 80f
            durationHours in 5.0f..6.0f -> 60f
            durationHours in 10.0f..11.0f -> 60f
            durationHours in 4.0f..5.0f -> 40f
            durationHours > 11.0f -> 40f
            else -> 20f  // < 4h
        }

        // 睡眠质量评分：1-5 映射到 0-100
        val qualityScore = (quality.coerceIn(1, 5) - 1) * 25f

        return (durationScore * 0.6f + qualityScore * 0.4f)
    }

    /**
     * 饮食维度评分（0-100）
     *
     * 子项权重：
     * - 卡路里达标率 60%：摄入在目标的 80%-120% 区间满分
     * - 记录完整性 40%：是否记录了三餐（早/午/晚各占 1/3）
     *
     * @param totalCalories 今日总摄入卡路里
     * @param targetCalories 用户设定的目标卡路里
     * @param mealCount 今日记录的不同餐次数量（早/午/晚/零食）
     * @param hasRecord 是否有饮食记录
     */
    fun calculateDietScore(
        totalCalories: Float,
        targetCalories: Int,
        mealCount: Int,
        hasRecord: Boolean
    ): Float {
        if (!hasRecord) return 0f

        val target = if (targetCalories > 0) targetCalories else 2000

        // 卡路里达标率：目标的 80%-120% 满分，偏离越多扣分越多
        val ratio = totalCalories / target
        val calorieScore = when {
            ratio in 0.8f..1.2f -> 100f
            ratio in 0.6f..0.8f || ratio in 1.2f..1.4f -> 75f
            ratio in 0.4f..0.6f || ratio in 1.4f..1.6f -> 50f
            ratio < 0.4f -> 25f
            else -> 25f  // > 1.6
        }

        // 记录完整性：3餐满分（早/午/晚），零食不计入
        val completenessScore = (mealCount.coerceAtMost(3).toFloat() / 3f * 100f)

        return (calorieScore * 0.6f + completenessScore * 0.4f)
    }

    /**
     * 根据总分返回健康等级
     */
    fun getHealthLevel(score: Float): HealthLevel = when {
        score >= 90 -> HealthLevel.EXCELLENT
        score >= 75 -> HealthLevel.GOOD
        score >= 60 -> HealthLevel.FAIR
        score >= 40 -> HealthLevel.POOR
        else -> HealthLevel.BAD
    }

    /**
     * 根据各维度评分生成个性化建议
     */
    fun generateAdvice(
        exerciseScore: Float,
        sleepScore: Float,
        dietScore: Float
    ): List<String> = buildList {
        // 运动建议
        when {
            exerciseScore >= 80 -> add("运动表现优秀，继续保持当前的运动习惯！")
            exerciseScore >= 60 -> add("运动量基本达标，建议适当增加步行或有氧运动时间。")
            exerciseScore >= 40 -> add("运动量偏少，建议每天至少步行30分钟，目标8000步。")
            else -> add("运动严重不足，建议从每天散步15分钟开始，逐步增加运动量。")
        }

        // 睡眠建议
        when {
            sleepScore >= 80 -> add("睡眠质量良好，继续保持规律的作息时间！")
            sleepScore >= 60 -> add("睡眠质量一般，建议保持7-9小时睡眠，避免熬夜。")
            sleepScore >= 40 -> add("睡眠不足，建议调整作息，减少睡前使用电子设备。")
            sleepScore > 0 -> add("睡眠质量较差，建议尽快调整作息，必要时咨询医生。")
            else -> add("今日未记录睡眠数据，建议每天记录睡眠情况以便跟踪。")
        }

        // 饮食建议
        when {
            dietScore >= 80 -> add("饮食记录完整且摄入合理，继续保持均衡饮食！")
            dietScore >= 60 -> add("饮食基本合理，建议注意三餐规律，控制热量摄入。")
            dietScore >= 40 -> add("饮食记录不完整，建议坚持记录每餐摄入，便于营养管理。")
            dietScore > 0 -> add("饮食摄入偏离目标较多，建议调整饮食结构。")
            else -> add("今日未记录饮食数据，建议记录三餐摄入以获得更准确的评分。")
        }
    }
}

enum class HealthLevel(val label: String, val emoji: String) {
    EXCELLENT("优秀", "🌟"),
    GOOD("良好", "😊"),
    FAIR("一般", "😐"),
    POOR("较差", "😟"),
    BAD("很差", "😞")
}

/**
 * 健康评分结果数据类
 */
data class HealthScore(
    val totalScore: Float = 0f,
    val exerciseScore: Float = 0f,
    val sleepScore: Float = 0f,
    val dietScore: Float = 0f,
    val level: HealthLevel = HealthLevel.BAD,
    val advices: List<String> = emptyList()
)
