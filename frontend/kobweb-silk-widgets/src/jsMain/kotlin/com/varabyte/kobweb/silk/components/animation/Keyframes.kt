package com.varabyte.kobweb.silk.components.animation

import com.varabyte.kobweb.compose.css.CSSAnimation
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.silk.theme.SilkConfig
import org.jetbrains.compose.web.css.*
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class KeyframesBuilder internal constructor() {
    internal val keyframeStyles = mutableMapOf<CSSKeyframe, () -> Modifier>()

    /** Describe the style of the element when this animation starts. */
    fun from(createStyle: () -> Modifier) {
        keyframeStyles += CSSKeyframe.From to createStyle
    }

    /** Describe the style of the element when this animation ends. */
    fun to(createStyle: () -> Modifier) {
        keyframeStyles += CSSKeyframe.To to createStyle
    }

    /** Describe the style of the element when the animation reaches some percent completion. */
    operator fun CSSSizeValue<CSSUnit.percent>.invoke(createStyle: () -> Modifier) {
        keyframeStyles += CSSKeyframe.Percentage(this) to createStyle
    }

    /**
     * A way to assign multiple percentage values with the same style.
     *
     * For example, this can be useful if you have an animation that changes, then stops for a bit, and then continues
     * to change again.
     *
     * ```
     * val Example by keyframes {
     *    from { Modifier.opacity(0) }
     *    each(20.percent, 80.percent) { Modifier.opacity(1) }
     *    to { Modifier.opacity(1) }
     * }
     * ```
     */
    fun each(vararg keys: CSSSizeValue<CSSUnit.percent>, createStyle: () -> Modifier) {
        keyframeStyles += CSSKeyframe.Combine(keys.toList()) to createStyle
    }
}

/**
 * Define a set of keyframes that can later be references in animations.
 *
 * For example,
 *
 * ```
 * val Bounce = Keyframes("bounce") {
 *   from { Modifier.translateX((-50).percent) }
 *   to { Modifier.translateX((50).percent) }
 * }
 *
 * // Later
 * Div(
 *   Modifier
 *     .size(100.px).backgroundColor(Colors.Red)
 *     .animation(Bounce.toAnimation(
 *       duration = 2.s,
 *       timingFunction = AnimationTimingFunction.EaseIn,
 *       direction = AnimationDirection.Alternate,
 *       iterationCount = IterationCount.Infinite
 *     )
 *     .toAttrs()
 * )
 * ```
 *
 * Important! If you are using Kobweb, its Gradle plugin will automatically do this for you, but otherwise, you will
 * have to use an `@InitSilk` block to register your keyframes:
 *
 * ```
 * val Bounce = Keyframes("bounce") { ... }
 * @InitSilk
 * fun initSilk(ctx: InitSilkContext) {
 *   ctx.config.registerKeyframes(Bounce)
 * }
 * ```
 *
 * Note: You should prefer to create keyframes using the [keyframes] delegate method to avoid needing to duplicate the
 * property name, e.g.
 *
 * ```
 * val Bounce by keyframes {
 *   from { Modifier.translateX((-50).percent) }
 *   to { Modifier.translateX((50).percent) }
 * }
 * ```
 */
class Keyframes(val name: String, internal val init: KeyframesBuilder.() -> Unit)

class KeyframesProvider(private val init: KeyframesBuilder.() -> Unit) {
    // e.g. "ExampleText" to "example_text"
    private fun String.titleCamelCaseToSnakeCase(): String {
        require(this.isNotBlank())

        val currentWord = StringBuilder()
        val words = mutableListOf<String>()

        this.forEach { c ->
            if (c.isUpperCase()) {
                if (currentWord.isNotEmpty()) {
                    words.add(currentWord.toString())
                    currentWord.clear()
                }
            }
            currentWord.append(c)
        }
        words.add(currentWord.toString())

        return words.joinToString("_") { it.decapitalize() }
    }

    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>
    ): Keyframes {
        val name = property.name.titleCamelCaseToSnakeCase()
        return Keyframes(name, init)
    }
}
//class KeyframesProvider(private val init: KeyframesBuilder.() -> Unit) {
//    // e.g. "ExampleText" to "example_text"
//    private fun String.titleCamelCaseToSnakeCase(): String {
//        require(this.isNotBlank())
//
//        val currentWord = StringBuilder()
//        val words = mutableListOf<String>()
//
//        this.forEach { c ->
//            if (c.isUpperCase()) {
//                if (currentWord.isNotEmpty()) {
//                    words.add(currentWord.toString())
//                    currentWord.clear()
//                }
//            }
//            currentWord.append(c)
//        }
//        words.add(currentWord.toString())
//
//        return words.joinToString("_") { it.decapitalize() }
//    }
//
//    operator fun provideDelegate(
//        thisRef: Any?,
//        property: KProperty<*>
//    ): ReadOnlyProperty<Any?, Keyframes> {
//        val name = property.name.titleCamelCaseToSnakeCase()
//        return ReadOnlyProperty { _, _ -> Keyframes(name, init) }
//    }
//}

fun SilkConfig.registerKeyframes(keyframes: Keyframes) = registerKeyframes(keyframes.name, keyframes.init)

/**
 * Construct a [Keyframes] instance where the name comes from the variable name.
 *
 * For example,
 *
 * ```
 * val Bounce by keyframes { ... }
 * ```
 *
 * creates a keyframe entry into the current site stylesheet with the name "bounce".
 *
 * Title camel case gets converted to snake case, so if the variable was called "AnimBounce", the final name added to
 * the style sheet would be "anim-bounce"
 *
 * Note: You can always construct a [Keyframes] object directly if you need to control the name, e.g.
 *
 * ```
 * // Renamed "Bounce" to "LegacyBounce" but don't want to break some old code.
 * val LegacyBounce = Keyframes("bounce") { ... }
 * ```
 */
fun keyframes(init: KeyframesBuilder.() -> Unit) = KeyframesProvider(init)

fun Keyframes.toAnimation(
    duration: CSSSizeValue<out CSSUnitTime>? = null,
    timingFunction: AnimationTimingFunction? = null,
    delay: CSSSizeValue<out CSSUnitTime>? = null,
    iterationCount: CSSAnimation.IterationCount? = null,
    direction: AnimationDirection? = null,
    fillMode: AnimationFillMode? = null,
    playState: AnimationPlayState? = null
) = CSSAnimation(
    name,
    duration,
    timingFunction,
    delay,
    iterationCount,
    direction,
    fillMode,
    playState
)