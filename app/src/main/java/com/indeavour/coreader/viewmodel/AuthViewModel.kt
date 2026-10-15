package com.indeavour.coreader.viewmodel

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.indeavour.coreader.repository.UserRepository
import com.indeavour.coreader.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val userRepository = UserRepository()

    /** Backfills `profiles/{uid}` for accounts created before display names moved there. */
    private fun ensureProfile(userId: String, email: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            userRepository.ensureOwnProfile(userId, email)
        }
    }



    fun login(email: String, password: String, onResult : (Boolean, String) -> Unit){
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener {
            if(it.isSuccessful) {
                it.result?.user?.let { user -> ensureProfile(user.uid, user.email) }
                onResult(true, "Login Successful")
            } else {
                onResult(false, it.exception?.localizedMessage ?: "Login Failed")
            }
        }
    }

    fun signup(email: String, password: String, onResult : (Boolean, String) -> Unit){
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener {
            if(it.isSuccessful) {
                val userId = it.result?.user?.uid
                if (userId == null) {
                    onResult(false, "Signup Failed")
                    return@addOnCompleteListener
                }
                createAccount(userId, email, onResult)
            } else {
                onResult(false, it.exception?.localizedMessage ?: "Signup Failed")
            }
        }
    }

    /**
     * Writes the private user document and the public profile together. The display name defaults
     * to the local part of the address rather than the whole address, which other group members
     * would otherwise see.
     */
    private fun createAccount(userId: String, email: String, onResult: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                userRepository.createAccount(
                    uid = userId,
                    email = email,
                    username = UserRepository.defaultUsernameFor(email)
                )
                onResult(true, "Signup Successful")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Signup Failed")
            }
        }
    }

    fun signInWithGoogle(context: Context, onResult : (Boolean, String) -> Unit){
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(R.string.client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = credentialManager.getCredential(context=context, request=request)
                if (result.credential is CustomCredential && result.credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                    val credential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    auth.signInWithCredential(credential).addOnCompleteListener {
                        if(it.isSuccessful) {
                            Log.d("Google Signin New", it.result?.additionalUserInfo?.isNewUser.toString())
                            val userId = it.result?.user?.uid
                            val email = it.result?.user?.email
                            val isNewUser = it.result?.additionalUserInfo?.isNewUser ?: false
                            // Report once: the account document is created before the caller is
                            // told the sign-in succeeded, so the library never loads without it.
                            if (isNewUser && userId != null && email != null) {
                                createAccount(userId, email, onResult)
                            } else {
                                if (userId != null) ensureProfile(userId, email)
                                onResult(true, "Google Signin Successful")
                            }
                        } else {
                            onResult(false, it.exception?.localizedMessage ?: "Google Signin Failed")
                        }
                    }
                } else {
                    onResult(false, "Credential is not of type Google ID")
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Google Signin Failed")
            }
        }
    }

    fun forgotPassword(){

    }

    fun resetPassword(){

    }

    fun signOut(){}
}