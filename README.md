🛠️ Passo a Passo de Instalação e Configuração
Passo 1: Habilitar API Services no Roblox Studio
Para que o sistema de banimento permanente e persistência de kicks funcione, você precisa ativar o banco de dados do seu jogo:
 * Abra o seu jogo no Roblox Studio.
 * Vá até a aba Home e clique em Game Settings (Configurações do Jogo).
 * Acesse a seção Security (Segurança).
 * Ative a opção Enable API Services (Habilitar Serviços de API / DataStore).
 * Clique em Save (Salvar).
Passo 2: Criar o Script do Servidor
 * No painel Explorer, navegue até ServerScriptService.
 * Adicione um novo Script (normal, não LocalScript) e renomeie para AntiCheatManager.
 * Cole o seguinte código:
-- ===============================================================
-- ANTI-CHEAT E GERENCIADOR DE MODERAÇÃO (SERVER-SIDE)
-- Localização: ServerScriptService -> AntiCheatManager
-- ===============================================================

local Players = game:GetService("Players")
local DataStoreService = game:GetService("DataStoreService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

-- Banco de dados para persistência de punições
local BanDataStore = DataStoreService:GetDataStore("SecurityPunishments_v1")

-- Evento de comunicação seguro com o cliente
local SecurityEvent = ReplicatedStorage:FindFirstChild("SecurityEvent")
if not SecurityEvent then
    SecurityEvent = Instance.new("RemoteEvent")
    SecurityEvent.Name = "SecurityEvent"
    SecurityEvent.Parent = ReplicatedStorage
end

-- Limites físicos permitidos
local MAX_WALKSPEED = 25
local MAX_JUMPHEIGHT = 60

-- Lista de Administradores Oficiais (Adicione seu UserId aqui)
local ServerAdmins = {
    [12345678] = true, -- Substitua 12345678 pelo seu UserId do Roblox
}

local function getPlayerData(player)
    local key = "Player_" .. player.UserId
    local success, result = pcall(function()
        return BanDataStore:GetAsync(key)
    end)
    
    if success and result then
        return result
    else
        return { KickCount = 0, IsBanned = false }
    end
end

local function savePlayerData(player, data)
    local key = "Player_" .. player.UserId
    pcall(function()
        BanDataStore:SetAsync(key, data)
    end)
end

local function applyPunishment(player, reason)
    local data = getPlayerData(player)
    data.KickCount = (data.KickCount or 0) + 1

    if data.KickCount >= 4 then
        data.IsBanned = true
        savePlayerData(player, data)
        
        SecurityEvent:FireClient(player, "WarnAndBan", "Você foi BANIDO PERMANENTEMENTE por tentativas repetidas de bypass.")
        task.wait(1)
        player:Kick("BANIDO PERMANENTEMENTE: Você atingiu o limite máximo de 4 infrações de segurança.")
    else
        savePlayerData(player, data)
        
        local warningMsg = string.format("Infração de segurança (%d/3 avisos). Alterar o ambiente do jogo é proibido.", data.KickCount)
        SecurityEvent:FireClient(player, "WarnAndKick", warningMsg)
        task.wait(1)
        player:Kick(string.format("Segurança: %s (Aviso %d/3)", reason, data.KickCount))
    end
end

-- Checagem no momento da entrada
Players.PlayerAdded:Connect(function(player)
    local data = getPlayerData(player)
    
    if data.IsBanned then
        player:Kick("Sua conta está banida permanentemente deste jogo por violação de segurança.")
        return
    end

    player.CharacterAdded:Connect(function(character)
        local humanoid = character:WaitForChild("Humanoid")

        task.spawn(function()
            while character and character.Parent do
                task.wait(1)
                if humanoid then
                    if humanoid.WalkSpeed > MAX_WALKSPEED then
                        applyPunishment(player, "Velocidade alterada anormalmente (SpeedHack)")
                        break
                    end
                    if humanoid.JumpHeight > MAX_JUMPHEIGHT or humanoid.JumpPower > 100 then
                        applyPunishment(player, "Força de pulo alterada anormalmente (HighJump)")
                        break
                    end
                end
            end
        end)
    end)
end)

-- Denúncias e notificações vindas do cliente
SecurityEvent.OnServerEvent:Connect(function(player, action, reason)
    if action == "ReportTamper" then
        applyPunishment(player, reason or "Modificação não autorizada detectada")
    end
end)

Passo 3: Criar o LocalScript do Cliente
 * No painel Explorer, navegue até StarterPlayer \rightarrow StarterPlayerScripts.
 * Adicione um novo LocalScript e renomeie para ClientMonitor.
 * Cole o seguinte código:
-- ===============================================================
-- MONITOR E NOTIFICADOR DE SEGURANÇA (CLIENT-SIDE)
-- Localização: StarterPlayer -> StarterPlayerScripts -> ClientMonitor
-- ===============================================================

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local StarterGui = game:GetService("StarterGui")

local LocalPlayer = Players.LocalPlayer
local SecurityEvent = ReplicatedStorage:WaitForChild("SecurityEvent", 10)

local function showNotification(title, text)
    pcall(function()
        StarterGui:SetCore("SendNotification", {
            Title = title,
            Text = text,
            Duration = 5
        })
    end)
end

if SecurityEvent then
    SecurityEvent.OnClientEvent:Connect(function(actionType, message)
        if actionType == "WarnAndKick" then
            showNotification("⚠️ AVISO DE SEGURANÇA", message)
        elseif actionType == "WarnAndBan" then
            showNotification("🚫 BANIMENTO PERMANENTE", message)
        end
    end)
end

local function monitorCharacter(character)
    local humanoid = character:WaitForChild("Humanoid", 5)
    if not humanoid then return end

    humanoid:GetPropertyChangedSignal("WalkSpeed"):Connect(function()
        if humanoid.WalkSpeed > 25 then
            if SecurityEvent then
                SecurityEvent:FireServer("ReportTamper", "Alteração de WalkSpeed detectada no cliente")
            end
        end
    end)
end

if LocalPlayer.Character then
    monitorCharacter(LocalPlayer.Character)
end

LocalPlayer.CharacterAdded:Connect(monitorCharacter)

⚙️ Regras do Sistema de Punição
| Infração | Ação Aplicada | Notificação ao Jogador | Efeito no DataStore |
|---|---|---|---|
| 1ª Infração | Kick do Servidor | Aviso 1/3 na tela e no motivo de desconexão | KickCount = 1 |
| 2ª Infração | Kick do Servidor | Aviso 2/3 na tela e no motivo de desconexão | KickCount = 2 |
| 3ª Infração | Kick do Servidor | Aviso 3/3 (Último Aviso) | KickCount = 3 |
| 4ª Infração | BAN PERMANENTE | Notificação de Banimento e Desconexão Imediata | IsBanned = true |
❓ Perguntas Frequentes (FAQ)
1. Se o jogador usar um executor e deletar o ClientMonitor, o que acontece?
O servidor continua monitorando os atributos físicos do personagem. Se o jogador tentar alterar a velocidade ou comandos no servidor sem o script local, o AntiCheatManager do servidor detectará a alteração diretamente e aplicará a punição da mesma forma.
2. Como posso desbanir um jogador para testes?
Para desbanir um jogador durante o desenvolvimento, execute o seguinte comando no Command Bar do Roblox Studio (substituindo o UserId):
game:GetService("DataStoreService"):GetDataStore("SecurityPunishments_v1"):SetAsync("Player_SEU_USERID_AQUI", { KickCount = 0, IsBanned = false })
print("Jogador desbanido com sucesso!")

"""
with open("README.md", "w", encoding="utf-8") as f:
f.write(readme_content)
print("Ficheiro README.md criado com sucesso!")
O seu arquivo Markdown está pronto
[file-tag: code-generated-file-0-1785961265049707650]

---

### O que está incluído no arquivo `README.md`:

1. **Explicação Detalhada do Travamento (O que aconteceu):**
   * **Armadilha Anti-Tamper do Adonis:** O Adonis possui verificações internas no cliente. Quando um *bypass* tenta hookar ou modificar suas variáveis na memória, o Adonis executa um *loop* infinito de segurança (`while true do end`), travando a thread do Roblox e congelando a tela.
   * **Sobrecarga da VM (Luraph):** O script de *bypass* roda sob uma Máquina Virtual em Lua que, ao tentar interceptar rotinas sem chamadas de espera (`task.wait()`), consome 100% da CPU do cliente.
   * **Desincronização com o Servidor (*Server Desync*):** Mesmo que o cliente consiga desativar o script localmente, o servidor continua exigindo pacotes de validação e desativa a interatividade.

2. **A Solução Defensiva ("A Cura"):**
   * Apresentação da arquitetura **Server-Side Authority**, onde o servidor é a única fonte da verdade e ignora qualquer alteração feita na memória do cliente.

3. **Guia Passo a Passo de Instalação no Roblox Studio:**
   * Ativação de **API Services / DataStore** nas configurações do jogo (`Game Settings -> Security`).
   * Estrutura de pastas e arquivos no Roblox Studio.
   * Código completo e comentado do **`ServerScriptService/AntiCheatManager.lua`** (Servidor).
   * Código completo do **`StarterPlayerScripts/ClientMonitor.lua`** (Cliente).

4. **Sistema de Punição Progressiva Persistente:**
   * **1ª à 3ª Infração:** Exibição de notificação no cliente, salvamento no DataStore e `Kick` indicando o número do aviso (`1/3`, `2/3`, `3/3`).
   * **4ª Infração:** **Banimento Permanente** gravado no banco de dados do Roblox, impedindo que o jogador volte a entrar no servidor.

5. **FAQ e Comando de Desbanimento:**
   * Código pronto para rodar no *Command Bar* do Roblox Studio caso precise desbanir uma conta durante os testes.

