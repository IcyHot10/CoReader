package com.example.coreader

import androidx.annotation.ContentView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
fun LoginScreen(){

    var isLogin by remember {
        mutableStateOf(true)
    }

    val setLogin = { isLogin = true }

    val setRegister = { isLogin = false}

    Box(){
        Image(painter = painterResource(R.drawable.login1), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize(), alignment = Alignment.TopCenter)
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(250.dp))
            if (isLogin) LoginCard() else RegisterCard()
            Spacer(modifier = Modifier.height(10.dp))
            Text("Forgot Password?", modifier = Modifier.clickable {})
            Spacer(modifier = Modifier.height(30.dp))
            Text("Or sign in with")
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.width(150.dp)) {
                Image(painter = painterResource(R.drawable.google), contentDescription = "Google Login", modifier = Modifier.size(30.dp).clickable{})
                Image(painter = painterResource(R.drawable.facebook), contentDescription = "Facebook Login", modifier = Modifier.size(30.dp).clickable{})
            }
        }
        if (isLogin) LoginText(Modifier.align(Alignment.BottomCenter).padding(20.dp)){setRegister} else RegisterText(Modifier.align(Alignment.BottomCenter).padding(20.dp)){setLogin}
    }
}

@Composable
fun LoginCard(modifier: Modifier = Modifier){
    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    OutlinedCard(modifier = modifier.wrapContentSize().defaultMinSize(300.dp, 50.dp).padding(3.dp), border = BorderStroke(2.dp, Teal)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(10.dp)) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Login", fontSize = 28.sp, color = Teal, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = email, onValueChange = { email= it}, label = {Text("Email")}, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Teal))
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = {Text("Password")}, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Teal))
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = {}) {
                Text("Login")
            }
        }
    }
}

@Composable
fun RegisterCard(modifier: Modifier = Modifier){
    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var confirmPassword by remember {
        mutableStateOf("")
    }

    OutlinedCard(modifier = modifier.wrapContentSize().defaultMinSize(300.dp, 50.dp).padding(3.dp), border = BorderStroke(2.dp, Teal)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(10.dp)) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Login", fontSize = 28.sp, color = Teal, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = email, onValueChange = { email= it}, label = {Text("Email")}, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Teal))
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = {Text("Password")}, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Teal))
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = confirmPassword, onValueChange = { password = it }, label = {Text("Password")}, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Teal))
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = {}) {
                Text("Register")
            }
        }
    }
}

@Composable
fun LoginText(modifier: Modifier = Modifier, function: () -> Unit){
    Row(modifier = modifier) {
        Text("Don't have an account? ")
        Text("Register", color = Teal, fontWeight = FontWeight.Bold, modifier = Modifier.clickable{function})
    }
}

@Composable
fun RegisterText(modifier: Modifier = Modifier, function: () -> Unit){
    Row(modifier = modifier) {
        Text("Already have an account? ")
        Text("Sign In", color = Teal, fontWeight = FontWeight.Bold, modifier = Modifier.clickable{function})
    }
}