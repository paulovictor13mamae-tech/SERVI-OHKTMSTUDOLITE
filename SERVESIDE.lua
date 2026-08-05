-- ===============================================================
-- SISTEMA DE MONITORAMENTO E MODERAÇÃO (SERVER-SIDE)
-- Localização: ServerScriptService
-- ===============================================================

local Players = game:GetService("Players")
local DataStoreService = game:GetService("DataStoreService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

-- DataStore para salvar histórico de punições
local BanDataStore = DataStoreService:GetDataStore("SecurityPunishments_v1")

-- Eventos de comunicação com o cliente
local SecurityEvent = ReplicatedStorage:FindFirstChild("SecurityEvent")
if not SecurityEvent then
    SecurityEvent = Instance.new("RemoteEvent")
    SecurityEvent.Name = "SecurityEvent"
    SecurityEvent.Parent = ReplicatedStorage
end

-- Limites físicos aceitáveis no servidor
local MAX_WALKSPEED = 25
local MAX_JUMPHEIGHT = 60

-- Carrega os dados do jogador (Histórico de Kicks e Status de Ban)
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

-- Salva os dados do jogador no DataStore
local function savePlayerData(player, data)
    local key = "Player_" .. player.UserId
    pcall(function()
        BanDataStore:SetAsync(key, data)
    end)
end

-- Aplica a punição (Kick ou Banimento)
local function applyPunishment(player, reason)
    local data = getPlayerData(player)
    data.KickCount = (data.KickCount or 0) + 1

    -- Se for a 4ª infração, aplica o Banimento Permanente
    if data.KickCount >= 4 then
        data.IsBanned = true
        savePlayerData(player, data)
        
        -- Avisa o LocalScript antes do desconexão final
        SecurityEvent:FireClient(player, "WarnAndBan", "Você foi BANIDO PERMANENTEMENTE por alterações não autorizadas repetidas.")
        task.wait(1)
        player:Kick("BANIDO PERMANENTEMENTE: Você atingiu o limite de 4 infrações de segurança.")
    else
        savePlayerData(player, data)
        
        -- Avisa o LocalScript e aplica o Kick
        local warningMsg = string.format("Infração detectada (%d/3 aviso). Mudar atributos do jogo é proibido.", data.KickCount)
        SecurityEvent:FireClient(player, "WarnAndKick", warningMsg)
        task.wait(1)
        player:Kick(string.format("Segurança: %s (Aviso %d/3)", reason, data.KickCount))
    end
end

-- 1. Verificação ao Entrar no Jogo
Players.PlayerAdded:Connect(function(player)
    local data = getPlayerData(player)
    
    -- Se já estiver banido no DataStore, desconecta imediatamente
    if data.IsBanned then
        player:Kick("Sua conta está banida permanentemente deste servidor.")
        return
    end

    -- Monitora o personagem quando nascer
    player.CharacterAdded:Connect(function(character)
        local humanoid = character:WaitForChild("Humanoid")
        local rootPart = character:WaitForChild("HumanoidRootPart")

        -- Validação contínua do estado físico no servidor
        task.spawn(function()
            while character and character.Parent do
                task.wait(1)
                
                if humanoid then
                    -- Detecta alteração anormal de velocidade ou pulo no servidor
                    if humanoid.WalkSpeed > MAX_WALKSPEED then
                        applyPunishment(player, "Velocidade alterada anormalmente")
                        break
                    end
                    
                    if humanoid.JumpHeight > MAX_JUMPHEIGHT or humanoid.JumpPower > 100 then
                        applyPunishment(player, "Força de pulo alterada anormalmente")
                        break
                    end
                end
            end
        end)
    end)
end)

-- 2. Recebe denúncias registradas pelo cliente
SecurityEvent.OnServerEvent:Connect(function(player, action, reason)
    if action == "ReportTamper" then
        applyPunishment(player, reason or "Anomalia detectada no cliente")
    end
end)
