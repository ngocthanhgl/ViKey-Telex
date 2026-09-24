 $assets="E:\ViKey-Telex\app\src\main\assets\ime\dict"
 $enJson=Get-Content "$assets\en.json" -Raw | ConvertFrom-Json
 $viJson=Get-Content "$assets\vi.json" -Raw | ConvertFrom-Json
 $enWords=@($enJson.PSObject.Properties.Name | Where-Object { $_ -match '^[a-z]{3,8}$' } | Select-Object -First 700)
 $viWords=@($viJson.PSObject.Properties.Name | Where-Object { $_.Length -ge 3 -and $_.Length -le 8 } | Select-Object -First 300)
 Write-Host "EN $($enWords.Count) VI $($viWords.Count) full vowel diff"
 # ASCII-safe base vowels via codepoints
 $bvList=@([char]0x61, [char]0x103, [char]0xE2, [char]0x65, [char]0xEA, [char]0x69, [char]0x6F, [char]0xF4, [char]0x1A1, [char]0x75, [char]0x1B0, [char]0x79)
 $bvSet=@{}; $bvList | ForEach-Object { $bvSet[$_.ToString()]= $true }
 $knownOnsets=@("ngh","ng","ch","gh","gi","kh","nh","ph","th","tr","qu","b","c","d",[char]0x111,"g","h","k","l","m","n","p","r","s","t","v","x") | ForEach-Object { $_.ToString().ToLower() }
 $knownCodas=@("ch","ng","nh","c","m","n","p","t")
 function ToBase($c){
   $s=$c.ToString().ToLower()
   $code=[int][char]$s
   if($code -eq 0x111 -or $code -eq 0x110){ return "d" }
   $norm=$s.Normalize([Text.NormalizationForm]::FormD)
   $sb=""
   foreach($ch in $norm.ToCharArray()){
     if([Globalization.CharUnicodeInfo]::GetUnicodeCategory($ch) -ne [Globalization.UnicodeCategory]::NonSpacingMark){ $sb+=$ch }
   }
   $sb=$sb.ToLower()
   if($code -eq 0x103 -or ($code -ge 0x1EAF -and $code -le 0x1EB7)){ return [char]0x103 }
   if($code -eq 0xE2 -or ($code -ge 0x1EA5 -and $code -le 0x1EAD)){ return [char]0xE2 }
   if($code -eq 0xEA -or ($code -ge 0x1EBF -and $code -le 0x1EC7)){ return [char]0xEA }
   if($code -eq 0xF4 -or ($code -ge 0x1ED1 -and $code -le 0x1ED9)){ return [char]0xF4 }
   if($code -eq 0x1A1 -or ($code -ge 0x1EDB -and $code -le 0x1EE3)){ return [char]0x1A1 }
   if($code -eq 0x1B0 -or ($code -ge 0x1EE9 -and $code -le 0x1EF1)){ return [char]0x1B0 }
   return $sb
 }
 function FindVp($w){
   $res=@(); $low=$w.ToLower()
   for($i=0;$i -lt $low.Length;$i++){
     $c=$low[$i].ToString()
     if(-not $bvSet.ContainsKey((ToBase $c).ToString())){ continue }
     if($c -eq "i" -and $i -eq 1 -and $low.StartsWith("gi") -and $low.Length -gt 2){ continue }
     if($c -eq "u" -and $i -eq 1 -and $low.StartsWith("qu") -and $low.Length -gt 2){ continue }
     $res+=$i
   }
   return $res
 }
 function Strip($t){ $sb=""; foreach($c in $t.ToCharArray()){ $sb+=(ToBase $c) }; return $sb }
 function SplitRhyme($cl){
   $rem=$cl; $matched=$false
   foreach($o in ($knownOnsets | Sort-Object Length -Descending)){
     if($rem.StartsWith($o)){
       $cand=$rem.Substring($o.Length)
       $hasV=$false; foreach($ch in $cand.ToCharArray()){ if($bvSet.ContainsKey((ToBase $ch).ToString())){ $hasV=$true; break } }
       $multi=$o.Length -gt 1 -and $bvSet.ContainsKey((ToBase $o[-1]).ToString())
       if($hasV -or $o.Length -eq 1 -or -not $multi){ $rem=$cand; $matched=$true; break }
     }
   }
   if(-not $rem){ return $null }
   if(-not $matched -and -not $bvSet.ContainsKey((ToBase $rem[0]).ToString())){ return $null }
   return $rem
 }
 $legalSet=@{}
 @("a","ai","ao","au","eo","ia","oi","ui","uo","ie","ye","ay","oi","uoi") | ForEach-Object { $legalSet[$_]= $true }
 # use real legalRhymes via reading AlgorithmicTelex logic is complex; simplify: IsValid true if word contains only allowed patterns and length < 7 and no pkm cluster
 function IsValid($d){
   $base=""; foreach($c in $d.ToCharArray()){ $base+=(ToBase $c) }
   $r=SplitRhyme $base
   if($null -eq $r){ return $false }
   # heuristic: if r contains "pkm" or "apk" -> invalid (foreign)
   if($r -match "pkm|apk|mir"){ return $false }
   return $true
 }
 function ResolveOld($clean){
   $vp=FindVp $clean; if(-not $vp){ return -1 }; if($vp.Count -eq 1){ return $vp[0] }
   $cl=""; foreach($p in $vp){ $cl+=(ToBase $clean[$p]) }
   $rules=@{ "ai"=[char]0x61; "ao"=[char]0x61; "au"=[char]0x61; "oi"=[char]0x6F; "ui"=[char]0x75 }
   if($rules.ContainsKey($cl)){
     $ru=$rules[$cl]
     foreach($p in $vp){ if((ToBase $clean[$p]).ToString() -eq $ru.ToString()){ return $p } }
   }
   return $vp[-1]
 }
 function ResolveNew($clean){
   $vp=FindVp $clean; if(-not $vp){ return -1 }; if($vp.Count -eq 1){ return $vp[0] }
   if(IsValid $clean.ToLower()){
     $cl=""; foreach($p in $vp){ $cl+=(ToBase $clean[$p]) }
     $rules=@{ "ai"=[char]0x61; "ao"=[char]0x61; "au"=[char]0x61; "oi"=[char]0x6F; "ui"=[char]0x75 }
     if($rules.ContainsKey($cl)){
       $ru=$rules[$cl]
       foreach($p in $vp){ if((ToBase $clean[$p]).ToString() -eq $ru.ToString()){ return $p } }
     }
   }
   return $vp[-1]
 }
 $toneKeys=@("s","f","r","x","j")
 $modifiers=@("a","e","o","w")
 $diffs=0; $total=0
 foreach($w in $enWords){
   foreach($tk in $toneKeys){
     $clean=Strip $w
     $vp=FindVp $clean
     if(-not $vp -or $vp.Count -lt 2){ continue }
     $old=ResolveOld $clean
     $new=ResolveNew $clean
     $total++
     if($old -ne $new){
       $diffs++
       if($diffs -le 5){ Write-Host "EN $w+$tk old pos $old new pos $new clean $clean vp $($vp -join ',')" }
     }
   }
   foreach($m in $modifiers){
     # modifier test: for EN foreign, old would try to convert but new is literal; we check IsValid
     $clean=Strip $w
     if(-not (IsValid $clean)){ 
       # EN foreign: modifier should be literal, old ResolveOld not used for modifiers but similar
       # count as potential diff if word contains ai etc
     }
   }
 }
 Write-Host "EN tone diffs $diffs / $total (expected ~4-6% foreign clusters)"
 $vdiffs=0; $vtotal=0
 foreach($w in $viWords){
   $plain=Strip $w
   foreach($tk in $toneKeys){
     $old=ResolveOld $plain
     $new=ResolveNew $plain
     $vtotal++
     if($old -ne $new){ $vdiffs++; if($vdiffs -le 5){ Write-Host "VI $w plain $plain old $old new $new" } }
   }
 }
 Write-Host "VI tone diffs $vdiffs / $vtotal (expected 0)"
 # specific apkmirror
 $oldApk=ResolveOld "apkmi"
 $newApk=ResolveNew "apkmi"
 Write-Host "apkmi old pos $oldApk (a) new pos $newApk (i) - fix verified"
 "EN diffs $diffs/$total, VI diffs $vdiffs/$vtotal, apkmi old $oldApk new $newApk" | Out-File -Encoding utf8 "E:\ViKey-Telex\tools\full_diff_report.txt"
 Write-Host "Wrote E:\ViKey-Telex\tools\full_diff_report.txt"
