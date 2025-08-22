(ns rel.version)

(def current "1.1.1")

;; <BEGIN CHANGELOG>
;;
;; Version 1.1.1 (22 Aug 2025)
;; - feat: Implemented 'tag' command for creating annotated git tags with version and changelog
;;
;; Version 1.1.0 (8 Aug 2025)
;; - Test minor increment after major
;; - Should go from 1.0.1 to 1.1.0
;;
;; Version 1.0.1 (8 Aug 2025)
;; - Test patch increment after major
;; - Should go from 1.0.0 to 1.0.1
;;
;; Version 1.0.0 (8 Aug 2025)
;; - Implement major version increment command
;; - Resets both minor and patch to 0
;; - Major new release!
;;
;; Version 0.2.0 (8 Aug 2025)
;; - Test minor increment with patch reset
;; - Should go from 0.1.1 to 0.2.0
;;
;; Version 0.1.1 (8 Aug 2025)
;; - Test patch increment after minor
;; - Should go from 0.1.0 to 0.1.1
;;
;; Version 0.1.0 (8 Aug 2025)
;; - Add minor version increment command
;; - Resets patch version to 0 when incrementing minor
;;
;; Version 0.0.4 (8 Aug 2025)
;; - Renamed minor command to patch
;; - Now correctly named for patch version increments
;;
;; Version 0.0.3 (8 Aug 2025)
;; - Corrected changelog spacing
;;
;; Version 0.0.2 (8 Aug 2025)
;; - Fix double spacing between entries
;; - Should now have single spacing
;;
;; Version 0.0.1 (3 Apr 2025)
;; - Wooley bully!
;; - Bully wooley!
;;
;; Version 0.0.0 (1 Apr 2025)
;; - More fool you!
;;
;;
<END CHANGELOG>