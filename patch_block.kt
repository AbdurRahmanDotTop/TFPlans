import java.io.File

fun main() {
    val file = File("app/src/main/java/com/techilyfly/tfplans/ui/edit/BlockEditor.kt")
    var content = file.readText()
    
    content = content.replace(
        "    toggleChecklistTrigger: Int,\n    modifier: Modifier = Modifier,\n    bodySp: TextUnit = 16.sp\n)",
        "    toggleChecklistTrigger: Int,\n    modifier: Modifier = Modifier,\n    bodySp: TextUnit = 16.sp,\n    textColor: Color = SecondaryColor,\n    iconColor: Color = PrimaryColor\n)"
    )
    
    content = content.replace(
        "color = SecondaryColor,\n                            fontSize = bodySp",
        "color = textColor,\n                            fontSize = bodySp"
    )
    
    content = content.replace(
        "tint = SecondaryColor.copy(alpha = 0.5f),\n                            modifier = Modifier",
        "tint = textColor.copy(alpha = 0.5f),\n                            modifier = Modifier"
    )
    
    content = content.replace(
        "background(if (block.isChecked) PrimaryColor else Color.Transparent)",
        "background(if (block.isChecked) iconColor else Color.Transparent)"
    )
    
    content = content.replace(
        "color = if (block.isChecked) PrimaryColor else SecondaryColor.copy(alpha = 0.5f)",
        "color = if (block.isChecked) iconColor else textColor.copy(alpha = 0.5f)"
    )
    
    content = content.replace(
        "color = if (block.isChecked) SecondaryColor.copy(alpha = 0.6f) else PrimaryColor",
        "color = if (block.isChecked) textColor.copy(alpha = 0.6f) else textColor"
    )

    content = content.replace(
        "cursorBrush = SolidColor(PrimaryColor)",
        "cursorBrush = SolidColor(iconColor)"
    )
    
    file.writeText(content)
}
