package `in`.project.enroute.feature.admin.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Singleton repository managing Firebase admin authentication state.
 * Exposes a reactive [isLoggedIn] flow so the nav bar and other UI
 * can observe login status without holding a ViewModel reference.
 */
object AdminAuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _isLoggedIn = MutableStateFlow(auth.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    val currentUser: FirebaseUser? get() = auth.currentUser

    init {
        // Allow email/password auth without reCAPTCHA during development.
        // Remove this line once reCAPTCHA Enterprise API is enabled in
        // Google Cloud Console for the production Firebase project.
        auth.firebaseAuthSettings.setAppVerificationDisabledForTesting(true)

        // Keep the flow in sync whenever Firebase reports a change
        auth.addAuthStateListener { firebaseAuth ->
            _isLoggedIn.value = firebaseAuth.currentUser != null
        }
    }

    /**
     * Register a new admin account.
     * @return null on success, or an error message string.
     */
    suspend fun register(email: String, password: String): String? {
        return try {
            auth.createUserWithEmailAndPassword(email, password).await()
            null
        } catch (e: Exception) {
            e.localizedMessage ?: "Registration failed"
        }
    }

    /**
     * Sign in with email/password.
     * @return null on success, or an error message string.
     */
    suspend fun login(email: String, password: String): String? {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            null
        } catch (e: Exception) {
            e.localizedMessage ?: "Login failed"
        }
    }

    /** Sign out the current user. */
    fun logout() {
        auth.signOut()
    }
}
