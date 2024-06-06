package org.ripex.namelessrpsign

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.block.Sign
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.Scanner
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.format.TextColor
import java.io.*
import java.net.URL
import java.net.URLConnection
import java.security.MessageDigest

val plainSerializer = PlainTextComponentSerializer.plainText()
val defaultFileName = "default_filename"

class NamelessRPSig : JavaPlugin(), Listener {
    override fun onEnable() {
        logger.info("Сканируем таблички 10.0")
        server.pluginManager.registerEvents(this, this)
    }

    override fun onDisable() {
        logger.info("О не больше не сканируем таблички!")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (command.name.equals("rp", true)) {
            if (sender is Player) {
                val player = sender
                if (args.isEmpty()) {
                    player.sendMessage(Component.text("Используйте команду /rp [word] [link] Word - Кодовое слово для вашего РП, Link - прямая ссылка на ваш РП", NamedTextColor.YELLOW))
                    return true
                }
                if (args.size == 1 && args[0].equals("list", ignoreCase = true)) {
                    val rpList = getRPListForPlayer(player.name)
                    if (rpList.isNotEmpty()) {
                        player.sendMessage(Component.text("Ваши ресурс-паки:", NamedTextColor.YELLOW))
                        for (rp in rpList) {
                            player.sendMessage(Component.text("Слово: ${rp.first}, Ссылка: ${rp.second}", NamedTextColor.GREEN))
                        }
                    } else {
                        player.sendMessage(Component.text("У вас нет добавленных ресурс-паков.", NamedTextColor.RED))
                    }
                    return true
                }
                if (args.size == 2) {
                    if (!isValidURL(args[0])) {
                        if (isValidURL(args[1])) {
                            if (!containsWord(args[0])) {
                                val fileUrl = args[1]
                                player.sendMessage(Component.text("Скачивание файла...", NamedTextColor.YELLOW))
                                val fileName = downloadFile(fileUrl, defaultFileName)
                                player.sendMessage(Component.text("Файл загружен!", NamedTextColor.GREEN))
                                player.sendMessage(Component.text("Расчет хэша...", NamedTextColor.YELLOW))
                                val hash = calculateSHA1(File(fileName))
                                player.sendMessage(Component.text("Хэш расчитан", NamedTextColor.GREEN))
                                val file = File(fileName)
                                if (file.delete()) {
                                    player.sendMessage(Component.text("Остаточный файл удален!", NamedTextColor.GREEN))
                                } else {
                                    player.sendMessage(Component.text("Не удалось удалить остаточный файл. ОБЯЗАТЕЛЬНО свяжитесь с администрацией!!!", NamedTextColor.RED))
                                }
                                saveWordLink(args[0], args[1], hash, player.name)
                                player.sendMessage(Component.text("Ваш РП загружен на сервер!", NamedTextColor.GREEN))
                            } else {
                                player.sendMessage(Component.text("Кодовое слово занято! Используйте другое.", NamedTextColor.RED))
                            }
                        } else {
                            player.sendMessage(Component.text("Неправильные аргументы!", NamedTextColor.RED))
                        }
                    } else {
                        player.sendMessage(Component.text("Неправильные аргументы!", NamedTextColor.RED))
                    }
                    return true
                }
            }
        }
        return false
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player: Player = event.player

        if (event.action == Action.RIGHT_CLICK_BLOCK) {
            val block = event.clickedBlock
            if (block != null && block.state is Sign) {
                val sign = block.state as Sign
                val dir_side = sign.getInteractableSideFor(player) // Используем getLineLegacy для получения строки
                val side = sign.getSide(dir_side)
                val firstLine = plainSerializer.serialize(side.line(0))

                if (firstLine.equals("[ResourcePack]")) {
                    player.sendMessage("РП Обнаружен")
                    val (link, hash) = findLinkAndHashForWord(plainSerializer.serialize(side.line(1)))
                    if (link != null && hash != null) {
                        player.setResourcePack(link, hash)
                        player.sendMessage(Component.text("Установка ресурспака...", NamedTextColor.GREEN))
                    } else {
                        player.sendMessage(Component.text("Неверно указанно кодовое слово!", NamedTextColor.RED))
                    }
                }
            }
        }
    }

    private fun isValidURL(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    // Функция для сохранения пары "Слово - ссылка - хэш" в файл
    fun saveWordLink(word: String, link: String, hash: String, playerName: String) {
        val file = File("rp.txt")
        try {
            val fileWriter = FileWriter(file, true) // Открываем файл в режиме добавления
            val bufferedWriter = BufferedWriter(fileWriter)

            // Записываем строку в файл в формате "Слово Ссылка Хэш Ник"
            bufferedWriter.write("$word $link $hash $playerName\n")

            // Закрываем файловые потоки
            bufferedWriter.close()
            fileWriter.close()
        } catch (e: IOException) {
            // Обрабатываем исключение при возникновении ошибки записи в файл
            e.printStackTrace()
        }
    }

    fun containsWord(word: String): Boolean {
        val file = File("rp.txt")

        return try {
            val scanner = Scanner(file)
            while (scanner.hasNextLine()) {
                if (scanner.nextLine().contains(word)) {
                    scanner.close()
                    return true
                }
            }
            scanner.close()
            false
        } catch (e: FileNotFoundException) {
            // Обрабатываем исключение, если файл не найден
            e.printStackTrace()
            false
        }
    }

    fun getRPListForPlayer(playerName: String): List<Pair<String, String>> {
        val file = File("rp.txt")
        val rpList = mutableListOf<Pair<String, String>>()

        try {
            val scanner = Scanner(file)
            while (scanner.hasNextLine()) {
                val line = scanner.nextLine()
                val parts = line.split(" ")
                if (parts[3] == playerName) {
                    rpList.add(Pair(parts[0], parts[1]))
                }
            }
            scanner.close()
        } catch (e: FileNotFoundException) {
            // Обрабатываем исключение, если файл не найден
            e.printStackTrace()
        }
        return rpList
    }

    fun findLinkAndHashForWord(word: String): Pair<String?, String?> {
        val file = File("rp.txt")

        return try {
            val scanner = Scanner(file)
            while (scanner.hasNextLine()) {
                val line = scanner.nextLine()
                val parts = line.split(" ")
                if (parts[0] == word) {
                    scanner.close()
                    return Pair(parts[1], parts[2])
                }
            }
            scanner.close()
            Pair(null, null) // Слово не найдено
        } catch (e: FileNotFoundException) {
            // Обрабатываем исключение, если файл не найден
            e.printStackTrace()
            Pair(null, null) // Слово не найдено
        }
    }

    fun downloadFile(fileUrl: String, defaultFileName: String): String {
        val url = URL(fileUrl)
        val connection: URLConnection = url.openConnection()
        connection.connect()

        // Получение имени файла из заголовков
        val contentDisposition = connection.getHeaderField("Content-Disposition")
        val fileName = contentDisposition?.let {
            val regex = Regex("filename=\"(.+?)\"")
            regex.find(it)?.groupValues?.get(1)
        } ?: defaultFileName

        val inputStream: InputStream = connection.getInputStream()
        val outputStream = FileOutputStream(fileName)

        val buffer = ByteArray(1024)
        var bytesRead: Int

        inputStream.use { input ->
            outputStream.use { output ->
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }

        return fileName
    }

    fun calculateSHA1(file: File): String {
        val buffer = ByteArray(1024)
        val sha1 = MessageDigest.getInstance("SHA-1")

        file.inputStream().use { inputStream ->
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                sha1.update(buffer, 0, bytesRead)
            }
        }

        val hashBytes = sha1.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
