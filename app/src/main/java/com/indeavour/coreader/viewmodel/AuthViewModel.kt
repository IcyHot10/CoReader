package com.indeavour.coreader.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.indeavour.coreader.model.UserModel

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

    fun forgotPassword(){

    }

    fun resetPassword(){

    }
}