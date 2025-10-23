package com.indeavour.coreader

import android.util.Patterns
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indeavour.coreader.ui.theme.Teal

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
            Spacer(modifier = Modifier.height(if (isLogin) 225.dp else 175.dp))
            if (isLogin) LoginCard(login = routeToLibrary) else RegisterCard( register = routeToLibrary)
            if (isLogin) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Forgot Password?", modifier = Modifier.clickable {}, color = MaterialTheme.colorScheme.onTertiary)
            }
            Spacer(modifier = Modifier.height(20.dp))
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

    var passwordIsError by rememberSaveable {
        mutableStateOf(false)
    }

    var passwordError by rememberSaveable {
        mutableStateOf("")
    }

    val passwordState = rememberTextFieldState()
    var passwordVisible by remember { mutableStateOf(false) }

    val validatePassword: () -> Unit = {
        if (passwordState.text.length < 8){
            passwordIsError = true
            passwordError = "Password must be at least 8 characters"
        } else {
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

    LaunchedEffect(passwordState.text) {
        if (passwordState.text.length >= 8){
            passwordIsError = false
            passwordError = ""
        }
    }

    OutlinedCard(modifier = modifier.wrapContentSize().defaultMinSize(300.dp, 50.dp).padding(3.dp), border = BorderStroke(2.dp, Teal)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(10.dp)) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Login", fontSize = 28.sp, color = Teal, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = email, onValueChange = { changeEmail(it) }, label = {Text("Email")},
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Teal), isError = emailIsError,
                supportingText = { if (emailIsError) Text(emailError)}, )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedSecureTextField(state = passwordState, label = {Text("Password")}, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Teal),
                isError = passwordIsError, supportingText = { if (passwordIsError) Text(passwordError)},
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                textObfuscationMode = if (passwordVisible)
                    TextObfuscationMode.Visible
                else
                    TextObfuscationMode.RevealLastTyped,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
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

    var passwordIsError by rememberSaveable {
        mutableStateOf(false)
    }

    var passwordError by rememberSaveable {
        mutableStateOf("")
    }

    val passwordState = rememberTextFieldState()
    var passwordVisible by remember { mutableStateOf(false) }

    val validatePassword: () -> Unit = {
        if (passwordState.text.length < 8){
            passwordIsError = true
            passwordError = "Password must be at least 8 characters"
        } else {
            passwordIsError = false
            passwordError = ""
        }
    }

    var confirmPasswordIsError by rememberSaveable {
        mutableStateOf(false)
    }

    var confirmPasswordError by rememberSaveable {
        mutableStateOf("")
    }

    val confirmPasswordState = rememberTextFieldState()
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val validateConfirmPassword: () -> Unit = {
        if (passwordState.text != confirmPasswordState.text){
            confirmPasswordIsError = true
            confirmPasswordError = "Passwords do not match"
        } else {
            confirmPasswordIsError = false
            confirmPasswordError = ""
        }
    }

    val performRegister = {
        validateEmail()
        validatePassword()
        validateConfirmPassword()
        if (!(emailIsError || passwordIsError || confirmPasswordIsError)){
            register()
        }
    }

    LaunchedEffect(passwordState.text, confirmPasswordState.text) {
        if (passwordState.text.length >= 8){
            passwordIsError = false
            passwordError = ""
        }
        if (passwordState.text == confirmPasswordState.text){
            confirmPasswordIsError = false
            confirmPasswordError = ""
        }
    }

    OutlinedCard(modifier = modifier.wrapContentSize().defaultMinSize(300.dp, 50.dp).padding(3.dp), border = BorderStroke(2.dp, Teal)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(10.dp)) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Login", fontSize = 28.sp, color = Teal, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = email, onValueChange = { changeEmail(it) }, label = {Text("Email")}, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Teal), isError = emailIsError, supportingText = { if (emailIsError) Text(emailError)})
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedSecureTextField(state = passwordState, label = {Text("Password")}, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Teal),
                isError = passwordIsError, supportingText = { if (passwordIsError) Text(passwordError)},
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                textObfuscationMode = if (passwordVisible)
                    TextObfuscationMode.Visible
                else
                    TextObfuscationMode.RevealLastTyped,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedSecureTextField(state = confirmPasswordState, label = {Text("Confirm Password")},
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Teal), isError = confirmPasswordIsError,
                supportingText = { if (confirmPasswordIsError) Text(confirmPasswordError)},
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                textObfuscationMode = if (confirmPasswordVisible)
                    TextObfuscationMode.Visible
                else
                    TextObfuscationMode.RevealLastTyped,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
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
