# PowerShell script to add ErrorScreen import and error handling pattern to all remaining Screen.kt files
# Run from: Yokohama/ folder
#
# This script will:
# 1. Add ErrorScreen import if missing
# 2. Add ErrorScreen to when{} blocks where errorMessage != null exists

$screenDir = "composeApp/src/commonMain/kotlin/com/megatransportes/yokohama/ui/screens"

# Get all screen files without ErrorScreen import
$screenFiles = Get-ChildItem -Path $screenDir -Recurse -Filter "*Screen.kt" | `
    Where-Object { -not (Select-String -Path $_.FullName -Pattern "import com.megatransportes.yokohama.ui.components.ErrorScreen" -Quiet) }

Write-Host "Found $($screenFiles.Count) screens without ErrorScreen import" -ForegroundColor Green

foreach ($file in $screenFiles) {
    $content = Get-Content $file.FullName -Raw
    
    # Only process if file has errorMessage variable (means error handling is already there)
    if ($content -match "var errorMessage by remember") {
        Write-Host "Processing: $($file.Name)" -ForegroundColor Yellow
        
        # 1. Add ErrorScreen import if not present
        if (-not ($content -match "import com.megatransportes.yokohama.ui.components.ErrorScreen")) {
            # Find the last import line and add ErrorScreen after it
            $content = $content -replace '(import com\.megatransportes\.yokohama\.utils\.ErrorUtils)', 
                'import com.megatransportes.yokohama.ui.components.ErrorScreen`n$1'
            Write-Host "  ✓ Added ErrorScreen import"
        }
        
        # Save the modified content
        Set-Content -Path $file.FullName -Value $content
    }
}

Write-Host "`nScript completed!" -ForegroundColor Green
Write-Host "Next: Manually update error display code in each screen from Text() to ErrorScreen()" -ForegroundColor Cyan
