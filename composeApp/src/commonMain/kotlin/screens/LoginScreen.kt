package screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.ApiClient
import api.EmpresaDto
import database.AppRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    repository: AppRepository,
    onLoginSuccess: () -> Unit
) {

    // ================= STATES =================
    var cnpj by remember { mutableStateOf("") }
    var nomeEmpresaBusca by remember { mutableStateOf("") }
    var empresaSelecionada by remember { mutableStateOf<EmpresaDto?>(null) }
    var usuario by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var senhaVisivel by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var empresas by remember { mutableStateOf<List<EmpresaDto>>(emptyList()) }
    var buscandoEmpresas by remember { mutableStateOf(false) }
    var mostrarSugestoes by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // ================= LOGO ANIMATION =================
    val scaleAnim = remember { Animatable(0.6f) }
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(200)
        scaleAnim.animateTo(1f, tween(600))
        alphaAnim.animateTo(1f, tween(600))
    }

    // ================= BUSCAR EMPRESAS =================
    LaunchedEffect(nomeEmpresaBusca) {
        // Só busca se não tem empresa selecionada E digitou pelo menos 3 caracteres
        if (nomeEmpresaBusca.length >= 3 && empresaSelecionada == null) {
            buscandoEmpresas = true
            delay(500) // Debounce
            try {
                val response = ApiClient.buscarEmpresas(nomeEmpresaBusca)
                if (response.status == "ok") {
                    empresas = response.empresas
                    mostrarSugestoes = empresas.isNotEmpty()
                } else {
                    empresas = emptyList()
                    mostrarSugestoes = false
                }
            } catch (e: Exception) {
                empresas = emptyList()
                mostrarSugestoes = false
            }
            buscandoEmpresas = false
        } else if (nomeEmpresaBusca.length < 3) {
            empresas = emptyList()
            mostrarSugestoes = false
        }
    }

    // ================= UI =================
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    // Login SEMPRE usa cores light, independente do tema
                    listOf(ui.AppColorsLight.Primary, ui.AppColorsLight.Secondary)
                )
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    mostrarSugestoes = false
                })
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(80.dp))

            Text(
                text = "Portal do Motorista",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(Modifier.height(40.dp))

            // ================= CARD LOGIN =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {

                    Text(
                        text = "Faça seu login",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ui.AppColorsLight.TextPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(24.dp))

                    // ================= BUSCA DE EMPRESA =================
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            OutlinedTextField(
                                value = nomeEmpresaBusca,
                                onValueChange = {
                                    nomeEmpresaBusca = it
                                    // Se começar a digitar novamente, limpa a seleção
                                    if (empresaSelecionada != null && it != empresaSelecionada?.nome_empresa) {
                                        empresaSelecionada = null
                                        cnpj = ""
                                    }
                                },
                                label = { Text("Nome da Empresa") },
                                leadingIcon = {
                                    Icon(Icons.Default.Business, null, tint = ui.AppColorsLight.Primary)
                                },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (buscandoEmpresas) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = ui.AppColorsLight.Primary
                                            )
                                        } else if (empresaSelecionada != null) {
                                            // Botão para trocar empresa
                                            IconButton(
                                                onClick = {
                                                    empresaSelecionada = null
                                                    cnpj = ""
                                                    nomeEmpresaBusca = ""
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Trocar empresa",
                                                    tint = ui.AppColorsLight.Error
                                                )
                                            }
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        "Digite o nome da empresa",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                            )

                            // ================= SUGESTÕES DE EMPRESAS =================
                            AnimatedVisibility(
                                visible = mostrarSugestoes && empresaSelecionada == null,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .padding(top = 4.dp),
                                    elevation = CardDefaults.cardElevation(4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    LazyColumn {
                                        items(empresas) { empresa ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        empresaSelecionada = empresa
                                                        cnpj = empresa.cnpj
                                                        nomeEmpresaBusca = empresa.nome_empresa
                                                        mostrarSugestoes = false
                                                        focusManager.clearFocus()
                                                    }
                                                    .padding(12.dp)
                                            ) {
                                                Text(
                                                    text = empresa.nome_empresa,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = "CNPJ: ${formatarCNPJ(empresa.cnpj)}",
                                                    fontSize = 12.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                            if (empresa != empresas.last()) {
                                                HorizontalDivider()
                                            }
                                        }
                                    }
                                }
                            }

                            // Mensagem quando digita menos de 3 caracteres
                            if (nomeEmpresaBusca.isNotEmpty() &&
                                nomeEmpresaBusca.length < 3 &&
                                empresaSelecionada == null) {
                                Text(
                                    text = "Digite pelo menos 3 caracteres",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ================= CNPJ (APARECE APÓS SELEÇÃO) =================
                    AnimatedVisibility(
                        visible = empresaSelecionada != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            OutlinedTextField(
                                value = formatarCNPJ(cnpj),
                                onValueChange = { },
                                label = { Text("CNPJ") },
                                leadingIcon = {
                                    Icon(Icons.Default.Badge, null, tint = ui.AppColorsLight.Primary)
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        tint = ui.AppColorsLight.Success
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = ui.AppColorsLight.Success,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledLeadingIconColor = ui.AppColorsLight.Primary,
                                    disabledTrailingIconColor = ui.AppColorsLight.Success
                                )
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // ================= USUÁRIO =================
                    OutlinedTextField(
                        value = usuario,
                        onValueChange = { usuario = it },
                        label = { Text("Usuário") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, null, tint = ui.AppColorsLight.Primary)
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { /* O foco será movido automaticamente */ }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(16.dp))

                    // ================= SENHA =================
                    OutlinedTextField(
                        value = senha,
                        onValueChange = { senha = it },
                        label = { Text("Senha") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, null, tint = ui.AppColorsLight.Primary)
                        },
                        trailingIcon = {
                            IconButton(onClick = { senhaVisivel = !senhaVisivel }) {
                                Icon(
                                    imageVector = if (senhaVisivel) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (senhaVisivel) "Ocultar senha" else "Mostrar senha",
                                    tint = ui.AppColorsLight.Primary
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        visualTransformation = if (senhaVisivel) VisualTransformation.None else PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // ================= STATUS =================
                    if (statusMsg != null) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = ui.AppColorsLight.Primary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = statusMsg!!,
                                fontSize = 14.sp,
                                color = ui.AppColorsLight.Primary
                            )
                        }
                    }

                    // ================= ERRO =================
                    if (erro != null) {
                        Spacer(Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = ui.AppColorsLight.Error.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = erro!!,
                                color = ui.AppColorsLight.Error,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // ================= BOTÃO ENTRAR =================
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            scope.launch {
                                loading = true
                                erro = null
                                statusMsg = "Autenticando..."

                                try {
                                    val resp = ApiClient.login(cnpj, usuario, senha)

                                    if (resp.status == "ok") {
                                        // Salvar token JWT para autenticação em todas as requests
                                        resp.token?.let { ApiClient.setToken(it) }

                                        repository.salvarMotorista(
                                            motoristaId = resp.motorista_id ?: "",
                                            nome = resp.nome ?: "",
                                            cnpj = cnpj,
                                            usuario = usuario,
                                            senha = senha
                                        )

                                        statusMsg = "Sincronizando dados..."

                                        try {
                                            val sync = ApiClient.syncDados()
                                            if (sync.status == "ok") {
                                                repository.salvarDestinos(
                                                    sync.destinos.map { it.id to it.nome }
                                                )
                                                repository.salvarEquipamentos(
                                                    sync.equipamentos.map { it.id to it.placa }
                                                )
                                            }
                                        } catch (_: Exception) {}

                                        onLoginSuccess()
                                    } else {
                                        erro = when {
                                            resp.mensagem?.contains("empresa", ignoreCase = true) == true ||
                                                    resp.mensagem?.contains("cnpj", ignoreCase = true) == true ->
                                                "Empresa não encontrada. Verifique o CNPJ."
                                            resp.mensagem?.contains("usuário", ignoreCase = true) == true ||
                                                    resp.mensagem?.contains("senha", ignoreCase = true) == true ->
                                                "Usuário ou senha incorretos."
                                            else -> resp.mensagem ?: "Erro ao fazer login."
                                        }
                                    }
                                } catch (e: Exception) {
                                    val mensagemErro = e.message?.lowercase() ?: ""

                                    if (mensagemErro.contains("empresa") ||
                                        mensagemErro.contains("cnpj") ||
                                        mensagemErro.contains("404") ||
                                        mensagemErro.contains("not found")) {
                                        erro = "Empresa não encontrada. Verifique o CNPJ."
                                    } else if (mensagemErro.contains("usuário") ||
                                        mensagemErro.contains("usuario") ||
                                        mensagemErro.contains("senha") ||
                                        mensagemErro.contains("password") ||
                                        mensagemErro.contains("401") ||
                                        mensagemErro.contains("unauthorized")) {
                                        erro = "Usuário ou senha incorretos."
                                    } else {
                                        // Fallback offline: verifica credenciais com hash
                                        if (repository.verificarCredenciaisOffline(cnpj, usuario, senha)) {
                                            onLoginSuccess()
                                        } else {
                                            val local = repository.getMotoristaLogado()
                                            if (local != null) {
                                                if (local.cnpj != cnpj) {
                                                    erro = "Empresa não encontrada. Verifique o CNPJ digitado."
                                                } else {
                                                    erro = "Usuário ou senha incorretos."
                                                }
                                            } else {
                                                erro = "Sem conexão com o servidor. Faça login online primeiro."
                                            }
                                        }
                                    }
                                }

                                loading = false
                                statusMsg = null
                            }
                        },
                        enabled = !loading && cnpj.isNotBlank() && usuario.isNotBlank() && senha.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ui.AppColorsLight.Primary)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "ENTRAR",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = "© 2026 LF System",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// Função auxiliar para formatar CNPJ
private fun formatarCNPJ(cnpj: String): String {
    if (cnpj.length != 14) return cnpj
    return "${cnpj.substring(0, 2)}.${cnpj.substring(2, 5)}.${cnpj.substring(5, 8)}/${cnpj.substring(8, 12)}-${cnpj.substring(12, 14)}"
}