@file:Suppress("TooManyFunctions")

package dev.detekt.rules.style

import com.intellij.psi.PsiElement
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.ShortenOptions
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.expectedType
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isSubClassOf
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.resolveSymbol
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.returnType
import org.jetbrains.kotlin.analysis.api.components.scopeContext
import org.jetbrains.kotlin.analysis.api.components.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtContainerNodeForControlStructureBody
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtWhenConditionIsPattern
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Reports qualified enum entries, sealed subtypes and companion object properties that can be written without their
 * qualifier, because context-sensitive resolution finds them from the expected type.
 *
 * Context-sensitive resolution is a preview feature, enabled with the `-Xcontext-sensitive-resolution` compiler flag.
 * This rule does nothing unless the flag is set.
 *
 * The rule does not report:
 * - Names that already resolve without the qualifier, like an enum entry used inside its own enum
 * - Sealed subtypes nested more than one level below the expected type
 * - Sealed objects and companion properties in an if or when branch passed as an argument, since the other branches can
 * change the expected type
 * - Arguments that decide a type parameter of a function with other type parameters too
 * - Imports that are only needed without context-sensitive resolution, like `import com.example.Role.Admin` or
 * `import com.example.State.Failure`
 *
 * See the [Kotlin docs](https://kotlinlang.org/docs/whatsnew22.html#preview-of-context-sensitive-resolution),
 * the [KEEP](https://github.com/Kotlin/KEEP/blob/main/proposals/KEEP-0379-context-sensitive-resolution.md) and
 * [KT-16768](https://youtrack.jetbrains.com/issue/KT-16768). Since the feature is in preview, its behavior and
 * this rule may change.
 *
 * <noncompliant>
 * enum class Role { Admin, Basic }
 *
 * sealed interface State {
 *     data object Loading : State
 *     data class Failure(val reason: String) : State
 * }
 *
 * class Color(val rgb: Int) {
 *     companion object {
 *         val Red = Color(0xFF0000)
 *     }
 * }
 *
 * fun takesRole(role: Role) = role
 * fun takesColor(color: Color) = color
 * fun takesState(state: State) = state
 *
 * fun foo(role: Role, state: State, fallback: Role?): Role {
 *     takesRole(Role.Admin)
 *     takesColor(Color.Red)
 *     takesState(State.Loading)
 *     val roles = listOf(Role.Admin, Role.Basic)
 *     if (state is State.Failure) return fallback ?: Role.Admin
 *     if (role == Role.Admin) return Role.Basic
 *     return when (role) {
 *         Role.Admin -> Role.Basic
 *         Role.Basic -> Role.Admin
 *     }
 * }
 * </noncompliant>
 *
 * <compliant>
 * enum class Role { Admin, Basic }
 *
 * sealed interface State {
 *     data object Loading : State
 *     data class Failure(val reason: String) : State
 * }
 *
 * class Color(val rgb: Int) {
 *     companion object {
 *         val Red = Color(0xFF0000)
 *     }
 * }
 *
 * fun takesRole(role: Role) = role
 * fun takesColor(color: Color) = color
 * fun takesState(state: State) = state
 *
 * fun foo(role: Role, state: State, fallback: Role?): Role {
 *     takesRole(Admin)
 *     takesColor(Red)
 *     takesState(Loading)
 *     val roles = listOf<Role>(Admin, Basic)
 *     if (state is Failure) return fallback ?: Admin
 *     if (role == Admin) return Basic
 *     return when (role) {
 *         Admin -> Basic
 *         Basic -> Admin
 *     }
 * }
 *
 * // The qualifier is needed when there is no expected type, or the argument decides it
 * fun bar(): List<Any> {
 *     val role = Role.Admin
 *     val any: Any = Role.Basic
 *     val single = listOf(Role.Admin)
 *     val failure = takesState(State.Failure("x"))
 *     return listOf(role, any, single, failure)
 * }
 * </compliant>
 */
class UseContextSensitiveResolution(config: Config) :
    Rule(config, "The qualifier can be dropped, context-sensitive resolution finds the name."),
    RequiresAnalysisApi {

    override fun visit(root: KtFile) {
        if (languageVersionSettings.supportsFeature(LanguageFeature.ContextSensitiveResolutionUsingExpectedType)) {
            super.visit(root)
        }
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)

        // Calls like State.Failure("x") have a call as the selector, so are skipped here
        val selector = expression.selectorExpression as? KtNameReferenceExpression ?: return
        // Only look at the whole chain, not Role.Admin inside Role.Admin.name
        val parent = expression.parent
        if (parent is KtDotQualifiedExpression && parent.receiverExpression == expression) return
        // Receivers like makeUser() or this can't name a class, so there is no need to analyze them
        if (!expression.receiverExpression.isNameChain()) return

        analyze(expression) {
            @OptIn(KaExperimentalApi::class)
            val symbol = selector.resolveSymbol() ?: return
            if (isInScope(expression, symbol)) return

            @OptIn(KaIdeApi::class)
            val command = collectPossibleReferenceShorteningsInElement(
                element = expression,
                shortenOptions = ShortenOptions(removeContextSensitiveResolutionQualifiers = true),
                classShortenStrategy = { ShortenStrategy.SHORTEN_IF_ALREADY_IMPORTED },
                callableShortenStrategy = { ShortenStrategy.SHORTEN_IF_ALREADY_IMPORTED },
            )

            @OptIn(KaIdeApi::class)
            val shortened = command.listOfQualifierToShortenInfo.any {
                it.qualifierToShorten.element == expression && it.shortenedReference == null
            }
            if (shortened) {
                report(expression, selector.getReferencedName(), "the expected type is known")
            }
        }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.typeArgumentList != null) return

        val callee = expression.calleeExpression as? KtNameReferenceExpression ?: return
        val qualified = qualifiedArguments(expression) ?: return
        val names = qualified.map { it.selectorExpression as? KtNameReferenceExpression ?: return }

        analyze(expression) {
            val resolved = expression.resolveToCall()?.successfulFunctionCallOrNull() ?: return

            // pairOf<A, B>(...) would need more than one type argument
            if (resolved.typeArgumentsMapping.size != 1) return
            val owner = commonOwner(names) ?: return
            if (!qualified.all { qualifiesWith(it, owner) }) return
            if (!qualified.all { decidesTypeArgument(resolved, it, owner) }) return

            val arguments = names.joinToString { it.getReferencedName() }
            val original = "${callee.text}(${qualified.joinToString { it.text }})"
            // val roles: List<Role> = listOf(Role.Admin) already gives listOf its type argument
            if (hasDeclaredType(expression)) {
                report(expression, "${callee.text}($arguments)", "the expected type is known", original)
            } else {
                // listOf<Response>(...) would be missing Response's own type arguments
                if (owner.typeParameters.isNotEmpty()) return
                // listOf<Role>(Admin) is no shorter than listOf(Role.Admin)
                if (qualified.size < 2) return
                val receiver = qualified.first().receiverExpression.text
                val reason = "a type argument gives the names an expected type"
                report(expression, "${callee.text}<$receiver>($arguments)", reason, original)
            }
        }
    }

    override fun visitTypeReference(typeReference: KtTypeReference) {
        super.visitTypeReference(typeReference)

        val nullable = typeReference.typeElement as? KtNullableType
        val userType = (nullable?.innerType ?: typeReference.typeElement) as? KtUserType ?: return
        if (userType.qualifier == null) return
        val name = userType.referenceExpression ?: return

        val subject = checkedValue(typeReference) ?: return

        analyze(typeReference) {
            val symbol = (typeReference.type as? KaClassType)?.symbol ?: return
            val owner = owner(symbol) ?: return
            val expected = if (subject is KtWhenExpression) subjectType(subject) else subject.expressionType
            if (expected?.isSearchedIn(owner) != true) return
            if (isInScope(typeReference, symbol, isType = true)) return
            val replacement = name.getReferencedName() + userType.typeArgumentList?.text.orEmpty()
            report(typeReference, if (nullable != null) "$replacement?" else replacement, "the expected type is known")
        }
    }

    // Whether the expected type is written in the code and matches, as in val roles: List<Role> = listOf(Role.Admin).
    // override val roles = listOf(Role.Admin) and lambda results like lazy { listOf(Role.Admin) } take their type
    // from the call instead
    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun hasDeclaredType(expression: KtExpression): Boolean {
        var child: PsiElement = expression
        var parent = child.parent
        while (parent.passesResultUp(child)) {
            child = parent
            parent = parent.parent
        }
        val declared = when (parent) {
            is KtCallableDeclaration -> parent !is KtFunctionLiteral && parent.typeReference != null

            is KtReturnExpression ->
                parent.getTargetLabel() == null &&
                    parent.getStrictParentOfType<KtNamedFunction>()?.typeReference != null

            is KtBinaryExpression -> parent.operationToken == KtTokens.EQ

            is KtValueArgument -> hasDeclaredParameterType(parent, child as KtExpression)

            else -> false
        }
        return declared && expression.expressionType?.let { expression.expectedType?.semanticallyEquals(it) } == true
    }

    // Whether the parameter's type doesn't depend on the function's type parameters, like roles: List<Role>
    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun hasDeclaredParameterType(argument: KtValueArgument, expression: KtExpression): Boolean {
        val call = argument.getStrictParentOfType<KtCallElement>()?.resolveToCall()?.successfulFunctionCallOrNull()
        val declared = call?.valueArgumentMapping?.get(expression)?.symbol?.returnType ?: return false

        @OptIn(KaExperimentalApi::class)
        return call.symbol.typeParameters.none { declared.mentions(it) }
    }

    // Parentheses and if, when and try branches pass their result to their parent
    private fun PsiElement.passesResultUp(child: PsiElement): Boolean =
        when (this) {
            is KtParenthesizedExpression,
            is KtContainerNodeForControlStructureBody,
            is KtIfExpression,
            is KtTryExpression,
            is KtCatchClause,
                -> true

            is KtWhenEntry -> expression == child

            is KtWhenExpression -> child is KtWhenEntry

            is KtBlockExpression -> statements.lastOrNull() == child && parent.let {
                it is KtContainerNodeForControlStructureBody || it is KtWhenEntry || it is KtTryExpression ||
                    it is KtCatchClause
            }

            else -> false
        }

    // The value being checked or cast, or the `when` whose subject is
    private fun checkedValue(typeReference: KtTypeReference): KtExpression? =
        when (val parent = typeReference.parent) {
            is KtIsExpression -> parent.leftHandSide
            is KtBinaryExpressionWithTypeRHS -> parent.left
            is KtWhenConditionIsPattern -> parent.getStrictParentOfType<KtWhenExpression>()
            else -> null
        }

    private fun KtExpression.isNameChain(): Boolean =
        when (this) {
            is KtNameReferenceExpression -> true

            is KtDotQualifiedExpression ->
                selectorExpression is KtNameReferenceExpression && receiverExpression.isNameChain()

            else -> false
        }

    private fun qualifiedArguments(expression: KtCallExpression): List<KtDotQualifiedExpression>? {
        val arguments = expression.valueArguments
        val qualified = arguments
            .filter { !it.isNamed() && it.getSpreadElement() == null }
            .mapNotNull { it.getArgumentExpression() as? KtDotQualifiedExpression }
        return qualified.takeIf { it.isNotEmpty() && it.size == arguments.size && it.shareReceiverName() }
    }

    // qualifiesWith needs every receiver to end in the owner's name, which listOf(Role.Admin, Method.Header) can't
    private fun List<KtDotQualifiedExpression>.shareReceiverName(): Boolean {
        val names = map { receiverName(it)?.getReferencedName() }
        return names.first() != null && names.distinct().size == 1
    }

    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun commonOwner(names: List<KtNameReferenceExpression>): KaNamedClassSymbol? =
        names
            .map { name ->
                @OptIn(KaExperimentalApi::class)
                val symbol = name.resolveSymbol() ?: return null
                if (isInScope(name, symbol)) return null
                owner(symbol) ?: return null
            }
            .distinct()
            .singleOrNull()

    // Whether the argument goes to a parameter typed by the function's own type parameter, like the T in
    // listOf's vararg parameter, and that type parameter is currently inferred as the owner class
    context(_: KaSession)
    private fun decidesTypeArgument(
        call: KaFunctionCall<*>,
        argument: KtExpression,
        owner: KaNamedClassSymbol,
    ): Boolean {
        val signature = call.valueArgumentMapping[argument] ?: return false
        val declared = signature.symbol.returnType
        return declared is KaTypeParameterType &&
            isDecidedByArgument(declared.symbol, call.symbol) &&
            signature.returnType.isClass(owner)
    }

    private fun report(element: KtElement, replacement: String, reason: String, original: String = element.text) =
        report(
            Finding(
                entity = Entity.from(element),
                message = "'$original' can be written as '$replacement', $reason.",
            ),
        )

    // Whether the qualifier names the class itself, like Role in Role.Admin or com.example.Role in
    // com.example.Role.Admin. In Color.Red the Color resolves to the companion, not the class
    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun qualifiesWith(expression: KtDotQualifiedExpression, owner: KaNamedClassSymbol): Boolean {
        val name = receiverName(expression) ?: return false
        if (name.getReferencedName() != owner.name.asString()) return false
        @OptIn(KaExperimentalApi::class)
        val symbol = name.resolveSymbol()
        val isCompanion = symbol is KaNamedClassSymbol &&
            symbol.classKind == KaClassKind.COMPANION_OBJECT &&
            symbol.containingDeclaration == owner
        return symbol == owner || isCompanion
    }

    // The last name in the receiver, like Role in com.example.Role.Admin
    private fun receiverName(expression: KtDotQualifiedExpression): KtNameReferenceExpression? {
        val receiver = expression.receiverExpression
        return ((receiver as? KtDotQualifiedExpression)?.selectorExpression ?: receiver) as? KtNameReferenceExpression
    }

    // The type whose scope the symbol is found in, if it's one context-sensitive resolution looks in
    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun owner(symbol: KaSymbol): KaNamedClassSymbol? {
        val owner = symbol.containingDeclaration as? KaNamedClassSymbol ?: return null
        return when (symbol) {
            is KaEnumEntrySymbol -> owner

            is KaNamedClassSymbol -> owner.takeIf {
                it.modality == KaSymbolModality.SEALED &&
                    symbol.classKind != KaClassKind.COMPANION_OBJECT &&
                    symbol.isSubClassOf(it)
            }

            is KaPropertySymbol -> (owner.containingDeclaration as? KaNamedClassSymbol)?.takeIf {
                owner.classKind == KaClassKind.COMPANION_OBJECT && !symbol.isExtension
            }

            else -> null
        }
    }

    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun subjectType(whenExpression: KtWhenExpression): KaType? =
        whenExpression.subjectVariable?.returnType ?: whenExpression.subjectExpression?.expressionType

    // Whether the arguments decide the type parameter, as they do for listOf and constructors.
    // Assert<T>.isEqualTo(expected: T) gets T from its receiver, not the argument
    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun isDecidedByArgument(parameter: KaTypeParameterSymbol, function: KaFunctionSymbol): Boolean {
        if (function is KaConstructorSymbol) return true
        if (parameter.containingDeclaration != function) return false
        val receiver = function.receiverParameter?.returnType ?: return true
        return !receiver.mentions(parameter)
    }

    private fun KaType.mentions(parameter: KaTypeParameterSymbol): Boolean =
        when (this) {
            is KaTypeParameterType -> symbol == parameter
            is KaClassType -> typeArguments.any { it.type?.mentions(parameter) == true }
            else -> false
        }

    // Java parameters have platform types like TimeUnit!
    private fun KaType.isClass(symbol: KaClassLikeSymbol): Boolean {
        val type = (this as? KaFlexibleType)?.lowerBound ?: this
        return (type as? KaClassType)?.symbol == symbol
    }

    // Context-sensitive resolution looks in the expected type's class, and in the sealed classes it is nested in
    // and extends, like Arrangement for Arrangement.Horizontal
    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun KaType.isSearchedIn(owner: KaNamedClassSymbol): Boolean {
        if (isClass(owner)) return true
        val type = (this as? KaFlexibleType)?.lowerBound ?: this
        val expected = (type as? KaClassType)?.symbol as? KaNamedClassSymbol ?: return false
        return generateSequence(expected.containingDeclaration as? KaNamedClassSymbol) {
            it.containingDeclaration as? KaNamedClassSymbol
        }
            .takeWhile { expected.isSubClassOf(it) }
            .any { it == owner && it.modality == KaSymbolModality.SEALED }
    }

    // A plain name that already resolves wins over context-sensitive resolution, whether it finds something
    // else, like an imported Admin class, or the target itself, as inside the class. In a type, only classes count
    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun isInScope(position: KtElement, target: KaSymbol, isType: Boolean = false): Boolean {
        val name = (target as? KaNamedSymbol)?.name ?: return true
        val context = position.containingKtFile.scopeContext(position)

        @OptIn(KaExperimentalApi::class)
        val receivers = context.implicitValues.map { it.type }
        return context.scopes.any { scoped ->
            scoped.scope.classifiers(name).any() ||
                (!isType && scoped.scope.callables(name).any { it.isReachableWith(receivers) })
        }
    }

    // An extension like `val Icons.Admin` is only found when an implicit receiver fits it
    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun KaCallableSymbol.isReachableWith(receivers: List<KaType>): Boolean {
        val receiver = receiverParameter?.returnType ?: return true
        return receiver is KaTypeParameterType || receivers.any { it.isSubtypeOf(receiver) }
    }
}
