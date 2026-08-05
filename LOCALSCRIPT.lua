-- ===============================================================
-- MONITOR E NOTIFICADOR LOCAL (CLIENT-SIDE)
-- Localização: StarterPlayer -> StarterPlayerScripts
-- ===============================================================

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local StarterGui = game:GetService("StarterGui")

local LocalPlayer = Players.LocalPlayer
local SecurityEvent = ReplicatedStorage:WaitForChild("SecurityEvent")

-- Função para exibir mensagem na tela do jogador
local function showSecurityNotification(title, text)
    pcall(function()
        StarterGui:SetCore("SendNotification", {
            Title = title,
            Text = text,
            Duration = 5
        })
    end)
end

-- Escuta ordens e alertas do servidor
SecurityEvent.OnClientEvent:Connect(function(actionType, message)
    if actionType == "WarnAndKick" then
        showSecurityNotification("⚠️ AVISO DE SEGURANÇA", message)
    elseif actionType == "WarnAndBan" then
        showSecurityNotification("🚫 BANIMENTO PERMANENTE", message)
    end
end)

-- Monitoramento local preventivo
local function monitorCharacter(character)
    local humanoid = character:WaitForChild("Humanoid", 5)
    if not humanoid then return end

    -- Monitora alterações nas propriedades locais
    humanoid:GetPropertyChangedSignal("WalkSpeed"):Connect(function()
        if humanoid.WalkSpeed > 25 then
            SecurityEvent:FireServer("ReportTamper", "Alteração de WalkSpeed detectada no cliente")
        end
    end)
end

if LocalPlayer.Character then
    monitorCharacter(LocalPlayer.Character)
end

LocalPlayer.CharacterAdded:Connect(monitorCharacter)
