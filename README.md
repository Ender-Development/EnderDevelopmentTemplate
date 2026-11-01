<!---freshmark header
output = [
	'#',
	'<img src="src/main/resources/{{mod_logo_path}}" alt="{{mod_name}}" height="42" width="42">',
    '{{mod_name}}'
	].join(' ')
-->
# <img src="src/main/resources/assets/template_mod/textures/logo.png" alt="Ender-Development.org Template" height="42" width="42"> Ender-Development.org Template
<!---freshmark /header -->

<!---freshmark description
output = [
link(shield("Kotlin", "Kotlin", "{{kotlin_version}}", "blue"), "https://kotlinlang.org/"),
link(shield("Java", "Java", "{{java_version}}", "blue"), "https://www.azul.com/downloads/?version=java-25-lts&package=jdk#zulu"),
link(shield("Maven artifact", "Maven", "{{root_package}}:{{mod_id}}", "blue"), "https://maven.ender-development.org/" + "{{root_package}}/{{mod_id}}/".replaceAll("\\.", "/")),
link(shield("Version", "Version", "{{mod_version}}", "blue"), "{{mod_url}}/commits/master"),
link(shield("License", "License", "LGPL-3.0", "blue"), "{{mod_url}}/blob/master/LICENSE"),
"",
"{{mod_description}}".replace("Kotlin", link("Kotlin", "https://kotlinlang.org/")).replace("Java", link("Java", "https://www.java.com/en/")).replace("Catalyx", link("Catalyx", "https://github.com/Ender-Development/Catalyx"))
].join("\n")
-->
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-blue.svg)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-25.0.5-blue.svg)](https://www.azul.com/downloads/?version=java-25-lts&package=jdk#zulu)
[![Maven artifact](https://img.shields.io/badge/Maven-org.ender__development%3Atemplate__mod-blue.svg)](https://maven.ender-development.org/org/ender_development/template_mod/)
[![Version](https://img.shields.io/badge/Version-0.1.0-blue.svg)](https://github.com/Ender-Development/EnderDevelopmentTemplate/commits/master)
[![License](https://img.shields.io/badge/License-LGPL--3.0-blue.svg)](https://github.com/Ender-Development/EnderDevelopmentTemplate/blob/master/LICENSE)

Universal [Java](https://www.java.com/en/) / [Kotlin](https://kotlinlang.org/) Template for Minecraft 1.12 utilizing the latest tooling. Contains additional support for [Catalyx](https://github.com/Ender-Development/Catalyx) when using Kotlin.
<!---freshmark /description -->
