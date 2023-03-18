#!/usr/bin/env bb
;
; A script for generating a quantum-random I Ching hexagram.
;
; Usage: bb iching.clj

(require '[cheshire.core :as json]
         '[babashka.curl :as curl]
         '[clojure.string :as str]
         )

(def hexagrams
  {1 "**Force**\n\n**䷀**\n\n***乾 (qián)***"
   2 "**Field**\n\n**䷁**\n\n***坤 (kūn)***"
   3 "**Sprouting**\n\n**䷂**\n\n***屯 (zhūn)***"
   4 "**Enveloping**\n\n**䷃**\n\n***蒙 (méng)***"
   5 "**Attending**\n\n**䷄**\n\n***需 (xū)***"
   6 "**Arguing**\n\n**䷅**\n\n***訟 (sòng)***"
   7 "**Leading**\n\n**䷆**\n\n***師 (shī)***"
   8 "**Grouping**\n\n**䷇**\n\n***比 (bǐ)***"
   9 "**Small Accumulating**\n\n**䷈**\n\n***小畜 (xiǎo xù)***"
   10 "**Treading**\n\n**䷉**\n\n***履 (lǚ)***"
   11 "**Pervading**\n\n**䷊**\n\n***泰 (tài)***"
   12 "**Obstruction**\n\n**䷋**\n\n***否 (pǐ)***"
   13 "**Concording People**\n\n**䷌**\n\n***同人 (tóng rén)***"
   14 "**Great Possessing**\n\n**䷍**\n\n***大有 (dà yǒu)***"
   15 "**Humbling**\n\n**䷎**\n\n***謙 (qiān)***"
   16 "**Providing-For**\n\n**䷏**\n\n***豫 (yù)***"
   17 "**Following**\n\n**䷐**\n\n***隨 (suí)***"
   18 "**Correcting**\n\n**䷑**\n\n***蠱 (gǔ)***"
   19 "**Nearing**\n\n**䷒**\n\n***臨 (lín)***"
   20 "**Viewing**\n\n**䷓**\n\n***觀 (guān)***"
   21 "**Gnawing Bite**\n\n**䷔**\n\n***噬嗑 (shì kè)***"
   22 "**Adorning**\n\n**䷕**\n\n***賁 (bì)***"
   23 "**Stripping**\n\n**䷖**\n\n***剝 (bō)***"
   24 "**Returning**\n\n**䷗**\n\n***復 (fù)***"
   25 "**Without Embroiling**\n\n**䷘**\n\n***無妄 (wú wàng)***"
   26 "**Great Accumulating**\n\n**䷙**\n\n***大畜 (dà xù)***"
   27 "**Swallowing**\n\n**䷚**\n\n***頤 (yí)***"
   28 "**Great Exceeding**\n\n**䷛**\n\n***大過 (dà guò)***"
   29 "**Gorge**\n\n**䷜**\n\n***坎 (kǎn)***"
   30 "**Radiance**\n\n**䷝**\n\n***離 (lí)***"
   31 "**Conjoining**\n\n**䷞**\n\n***咸 (xián)***"
   32 "**Persevering**\n\n**䷟**\n\n***恆 (héng)***"
   33 "**Retiring**\n\n**䷠**\n\n***遯 (dùn)***"
   34 "**Great Invigorating**\n\n**䷡**\n\n***大壯 (dà zhuàng)***"
   35 "**Prospering**\n\n**䷢**\n\n***晉 (jìn)***"
   36 "**Darkening of the Light**\n\n**䷣**\n\n***明夷 (míng yí)***"
   37 "**Dwelling People**\n\n**䷤**\n\n***家人 (jiā rén)***"
   38 "**Polarising**\n\n**䷥**\n\n***睽 (kuí)***"
   39 "**Limping**\n\n**䷦**\n\n***蹇 (jiǎn)***"
   40 "**Taking-Apart**\n\n**䷧**\n\n***解 (xiè)***"
   41 "**Diminishing**\n\n**䷨**\n\n***損 (sǔn)***"
   42 "**Augmenting**\n\n**䷩**\n\n***益 (yì)***"
   43 "**Displacement**\n\n**䷪**\n\n***夬 (guài)***"
   44 "**Coupling**\n\n**䷫**\n\n***姤 (gòu)***"
   45 "**Clustering**\n\n**䷬**\n\n***萃 (cuì)***"
   46 "**Ascending**\n\n**䷭**\n\n***升 (shēng)***"
   47 "**Confining**\n\n**䷮**\n\n***困 (kùn)***"
   48 "**Welling**\n\n**䷯**\n\n***井 (jǐng)***"
   49 "**Skinning**\n\n**䷰**\n\n***革 (gé)***"
   50 "**Holding**\n\n**䷱**\n\n***鼎 (dǐng)***"
   51 "**Shake**\n\n**䷲**\n\n***震 (zhèn)***"
   52 "**Bound**\n\n**䷳**\n\n***艮 (gèn)***"
   53 "**Infiltrating**\n\n**䷴**\n\n***漸 (jiàn)***"
   54 "**Converting the Maiden**\n\n**䷵**\n\n***歸妹 (guī mèi)***"
   55 "**Abounding**\n\n**䷶**\n\n***豐 (fēng)***"
   56 "**Sojourning**\n\n**䷷**\n\n***旅 (lǚ)***"
   57 "**Ground**\n\n**䷸**\n\n***巽 (xùn)***"
   58 "**Open**\n\n**䷹**\n\n***兌 (duì)***"
   59 "**Dispersing**\n\n**䷺**\n\n***渙 (huàn)***"
   60 "**Articulating**\n\n**䷻**\n\n***節 (jié)***"
   61 "**Center Returning**\n\n**䷼**\n\n***中孚 (zhōng fú)***"
   62 "**Small Exceeding**\n\n**䷽**\n\n***小過 (xiǎo guò)***"
   63 "**Already Fording**\n\n**䷾**\n\n***既濟 (jì jì)***"
   64 "**Not Yet Fording**\n\n**䷿**\n\n***未濟 (wèi jì)***"})

(defn uint8->64
  "Convert a uint8 (0-255) to a 0-63 integer"
  [n]
  (mod n 64))

(defn qrand
  "Get a quantum random number between 1 and 64 (inclusive)
   by calling https://qrng.anu.edu.au"
  []
  (let [url "https://qrng.anu.edu.au/API/jsonI.php?length=1&type=uint8&size=1"]
    (-> (curl/get url)
        :body
        (json/parse-string true)
        :data
        first
        uint8->64
        inc)))

(defn ref-url
  "Generate a url for reference to the specific hexagram."
  [n]
  (str/join "" ["http://the-iching.com/hexagram_" n]))

(defn -main []
  (let [n (try (qrand) (catch Exception e (inc (rand-int 64))))
        hexagram (get hexagrams n)
        description (-> hexagram (str/replace "*" "") (str/split #"\n\n"))]
    (println (str/join " " [ (get description 1) (get description 0)]))
    (println (ref-url n))))

(-main)
