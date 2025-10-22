package com.example.coreader

import android.util.Patterns
import androidx.annotation.ContentView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coreader.ui.theme.Purple80
import com.example.coreader.ui.theme.Teal
import com.example.coreader.ui.theme.White

@Composable
fun LoginScreen(routeToLibrary : () -> Unit){

    var isLogin by remember {
        mutableStateOf(true)
    }

    val setLogin = { isLogin = true }

    val setRegister = { isLogin = false }

    Box(modifier = Modifier.background(MaterialTheme.colorScheme.tertiary)){
        Image(painter = painterResource(R.drawable.invis_background), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize(), alignment = Alignment.TopCenter)
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(if (isLogin) 250.dp else 200.dp))
            if (isLogin) LoginCard(login = routeToLibrary) else RegisterCard( register = routeToLibrary)
            if (isLogin) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Forgot Password?", modifier = Modifier.clickable {}, color = MaterialTheme.colorScheme.onTertiary)
            }
            Spacer(modifier = Modifier.height(30.dp))
            Text("Or sign in with", color = MaterialTheme.colorScheme.onTertiary)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.width(150.dp)) {
                Image(painter = painterResource(R.drawable.google), contentDescription = "Google Login", modifier = Modifier.size(30.dp).clickable{})
                Image(painter = painterResource(R.drawable.facebook), contentDescription = "Facebook Login", modifier = Modifier.size(30.dp).clickable{})
            }
        }
        if (isLogin) LoginText(Modifier.align(Alignment.BottomCenter).padding(WindowInsets.navigationBars.asPaddingValues())){setRegister()} else RegisterText(Modifier.align(Alignment.BottomCenter).padding(WindowInsets.navigationBars.asPaddingValues())){setLogin()}
    }
}

@Composable
fun LoginCard(modifier: Modifier = Modifier, login: () -> Unit){
    var email by rememberSaveable {
        mutableStateOf("")
    }

    var emailIsError by rememberSaveable {
        mutableStateOf(false)
    }

    var emailError by rememberSaveable {
        mutableStateOf("")
    }

    val validateEmail: () -> Unit = {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches() || email.isBlank()){
            emailIsError = true
            emailError = "Invalid email"
        } else {
            emailIsError = false
            emailError = ""
        }
    }

    val changeEmail: (String) -> Unit = {
        email = it
        if (Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            emailIsError = false
            emailError = ""
        }
    }

    var password by rememberSaveable {
        mutableStateOf("")
    }

    var passwordIsError by rememberSaveable {
        mutableStateOf(false)
    }

    var passwordError by rememberSaveable {
        mutableStateOf("")
    }

    val validatePassword: () -> Unit = {
        if (password.length < 8){
            passwordIsError = true
            passwordError = "Password must be at least 8 characters"
        } else {
            passwordIsError = false
            passwordError = ""
        }
    }

    val changePassword: (String) -> Unit = {
        password = it
        if (password.length >= 8){
            passwordIsError = false
            passwordError = ""
        }
    }

    val performLogin = {
        validateEmail()
        validatePassword()
        if (!(emailIsError || passwordIsError)){
            login()
        }
    }

    OutlinedCard(modifier = modifier.wrapContentSize().defaultMinSize(300.dp, 50.dp).padding(3.dp), border = BorderStroke(2.dp, Teal)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(10.dp)) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Login", fontSize = 28.sp, color = Teal, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = email, onValueChange = { changeEmail(it) }, label = {Text("Email")}, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Teal), isError = emailIsError, supportingText = { if (emailIsError) Text(emailError)})
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = password, onValueChange = { changePassword(it) }, label = {Text("Password")}, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Teal), isError = passwordIsError, supportingText = { if (passwordIsError) Text(passwordError)})
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = {performLogin()}) {
                Text("Login")
            }
        }
    }
}

@Composable
fun RegisterCard(modifier: Modifier = Modifier, register: () -> Unit){

    var email by rememberSaveable {
        mutableStateOf("")
    }

    var emailIsError by rememberSaveable {
        mutableStateOf(false)
    }

    var emailError by rememberSaveable {
        mutableStateOf("")
    }

    val validateEmail: () -> Unit = {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches() || email.isBlank()){
            emailIsError = true
            emailError = "Invalid email"
        } else {
            emailIsError = false
            emailError = ""
        }
    }

    val changeEmail: (String) -> Unit = {
        email = it
        if (Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            emailIsError = false
            emailError = ""
        }
    }

    var password by rememberSaveable {
        mutableStateOf("")
    }

    var passwordIsError by rememberSaveable {
        mutableStateOf(false)
    }

    var passwordError by rememberSaveable {
        mutableStateOf("")
    }

    val validatePassword: () -> Unit = {
        if (password.length < 8){
            passwordIsError = true
            passwordError = "Password must be at least 8 characters"
        } else {
            passwordIsError = false
            passwordError = ""
        }
    }

    val changePassword: (String) -> Unit = {
        password = it
        if (password.length >= 8){
            passwordIsError = false
            passwordError = ""
        }
    }

    var confirmPassword by rememberSaveable {
        mutableStateOf("")
    }

    var confirmPasswordIsError by rememberSaveable {
        mutableStateOf(false)
    }

    var confirmPasswordError by rememberSaveable {
        mutableStateOf("")
    }

    val validateConfirmPassword: () -> Unit = {
        if (password != confirmPassword){
            confirmPasswordIsError = true
            confirmPasswordError = "Passwords do not match"
        } else {
            confirmPasswordIsError = false
            confirmPasswordError = ""
        }
    }

    val changeConfirmPassword: (String) -> Unit = {
        confirmPassword = it
        if (password == confirmPassword){
            confirmPasswordIsError = false
            confirmPasswordError = ""
        }
    }

    val performRegister = {
        validateEmail()
        validatePassword()
        validateConfirmPassword()
        if (!(emailIsError || passwordIsError || confirmPasswordIsError)){
            register
        }
    }

    OutlinedCard(modifier = modifier.wrapContentSize().defaultMinSize(300.dp, 50.dp).padding(3.dp), border = BorderStroke(2.dp, Teal)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(10.dp)) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Login", fontSize = 28.sp, color = Teal, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = email, onValueChange = { changeEmail(it) }, label = {Text("Email")}, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Teal), isError = emailIsError, supportingText = { if (emailIsError) Text(emailError)})
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = password, onValueChange = { changePassword(it) }, label = {Text("Password")}, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Teal), isError = passwordIsError, supportingText = { if (passwordIsError) Text(passwordError)}, )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = confirmPassword, onValueChange = { changeConfirmPassword(it) }, label = {Text("Confirm Password")}, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Teal), isError = confirmPasswordIsError, supportingText = { if (confirmPasswordIsError) Text(confirmPasswordError)})
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = {performRegister()}) {
                Text("Register")
            }
        }
    }
}

@Composable
fun LoginText(modifier: Modifier = Modifier, function: () -> Unit){
    Row(modifier = modifier) {
        Text("Don't have an account? ", color = MaterialTheme.colorScheme.onTertiary)
        Text("Register", color = Teal, fontWeight = FontWeight.Bold, modifier = Modifier.clickable{function()})
    }
}

@Composable
fun RegisterText(modifier: Modifier = Modifier, function: () -> Unit){
    Row(modifier = modifier) {
        Text("Already have an account? ", color = MaterialTheme.colorScheme.onTertiary)
        Text("Sign In", color = Teal, fontWeight = FontWeight.Bold, modifier = Modifier.clickable{function()})
    }
}