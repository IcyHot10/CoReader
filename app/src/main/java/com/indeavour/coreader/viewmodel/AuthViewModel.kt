package com.indeavour.coreader.viewmodel

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.indeavour.coreader.model.UserModel
import com.indeavour.coreader.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()



    fun login(email: String, password: String, onResult : (Boolean, String) -> Unit){
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener {
            if(it.isSuccessful) {
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
                val userModel = UserModel(userId!!, email, email, null)
                firestore.collection("users").document(userId).set(userModel).addOnCompleteListener { dbTask ->
                    if(dbTask.isSuccessful){
                        onResult(true, "Signup Successful")
                    } else {
                        onResult(false, dbTask.exception?.localizedMessage ?: "Signup Failed")
                    }
                }
            } else {
                onResult(false, it.exception?.localizedMessage ?: "Signup Failed")
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
                            onResult(true, "Google Signin Successful")
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