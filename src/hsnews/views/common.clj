(ns hsnews.views.common
  (:use noir.core
        [hiccup.page-helpers :only [include-css html5 link-to]]
        somnium.congomongo)
  (:require [clojure.string :as string]
            [noir.response :as resp]
            [noir.request :as req]
            [hsnews.models.user :as users]
            [hsnews.models.post :as posts]
            [hsnews.models.comment :as comments]
            [noir.session :as session]
            [hsnews.utils :as utils]))

(defn get-request-uri []
  (:uri (req/ring-request)))

(defn get-referer []
  ((:headers (req/ring-request)) "referer"))

(pre-route "/*" {:keys [uri]}
           (when-not (or
                      (users/current-user)
                      (= uri "/login")
                      (= uri "/") ;; can view main page without registration
                      (re-find #"/posts/.*" uri) ;; can view posts without registration
                      (= uri "/sessions/create")
                      (= uri "/bookmarklet")
                      (= uri "/register") ;; uncomment to allow registration
                      (= uri "/users/create") ;; uncomment to allow registration
                      (re-find #"^/(css)|(img)|(js)|(favicon)" uri))
             (.println System/out (str "uri = " uri))
             (session/flash-put! (get-request-uri))
             (resp/redirect "/login")))

(defn extract-domain-from-url [url]
  (second (re-find #"^(?:[^:/]*://)?(?:www\.)?([^/\?]+)(?:.*)$" url)))

(defn format-points [points]
  (str points " point" (if (not= points 1) "s")))

(defn comment-points [{:keys [points] :as com}]
  (if (comments/is-author? com) (format-points points)))

(defpartial user-link [hs_id]
  (link-to {:class "userLink"} (str "/users/" hs_id) (users/get-username hs_id)))

(defpartial comment-link [c_id text]
  (link-to {:class "userLink"} (str "/comments/" c_id) text))

(defpartial upvote-comment-link [com]
  (if (comments/is-author? com) [:span.isAuthor.indicator "*"])
  (if-not (comments/voted? com)
    (link-to {:class "upvote indicator"} (comments/upvote-url com) "&#9650;")))

(defpartial comment-count [{:keys [_id score] :as post}]
            (let [comment-count (fetch-count :comments :where {:post_id _id})]
              (link-to {:title score} (posts/post-url post) (str comment-count " comment" (if (not= comment-count 1) "s" "")))))

(defpartial comment-subtext [{:keys [ts author points post_id _id] :as com}]
            [:div.subtext.comment
             (upvote-comment-link com)
             [:span.points (comment-points com)]
             (.println System/out (str "com = " com))
             [:span.author (user-link author)]
             [:span.date (str (utils/time-ago ts) " ")]
             [:span.link (comment-link _id "link")]])

(defpartial comment-item [{:keys [_id body] :as com} & {:keys [indent] :or {indent 0}}]
            [:li
             (comment-subtext com)
             [:div.commentBody (string/replace body "\n" "<br />")]
             [:div.subtext.comment (comment-link _id "reply")]
             (if (:replies com)
               [:ol.replies (map #(comment-item % :indent (inc indent))
                                 (:replies com))])])
(defpartial comment-item-recent [{:keys [_id body] :as com}] ;; don't walk replies
            [:li
             (comment-subtext com)
             [:div.commentBody (string/replace body "\n" "<br />")]
             [:div.subtext.comment (comment-link _id "reply")]])
             

(defpartial comment-list [comments]
    (if (not-empty comments)
      [:ol.commentList (map comment-item comments)]
      [:div.empty "No comments"]))


(defpartial comment-list-recent [comments] ;; don't walk replies
    (if (not-empty comments)
      [:ol.commentList (map comment-item-recent comments)]
      [:div.empty "No comments"]))


(defpartial upvote-link [post]
  (if (posts/is-author? post) [:span.isAuthor.indicator "*"])
  (if-not (posts/voted? post)
    (link-to {:class "upvote indicator"} (posts/upvote-url post) "&#9650;")))

(defpartial post-subtext [{:keys [ts author points] :as post}]
            [:div.subtext
              [:span.points (format-points points)]
              [:span.author (user-link author)]
              [:span.date (utils/time-ago ts)]
              [:span.commentCount (comment-count post)]])

(defpartial post-item [{:keys [link title author ts desc] :as post}]
  (when post
    (let [ link (if (seq link) link (posts/post-url post))]
      [:li.post
       [:h3.title
        ;;(.println System/out (str "desc = " desc "link = " link ))
        ;;(.println System/out (str "post = " post ))
        (upvote-link post)
        (link-to {:class "postLink"} link title)
       [:span.domain "(" (or (extract-domain-from-url link) "Self") ")"]] ;; tag as self for description posts
      (post-subtext post)])))

(defpartial post-list [items]
            (if (not-empty items)
              [:ol.postList (map post-item items)]
              [:div.empty "No posts"]))

(defpartial error-text [errors]
  [:span.error (string/join " " errors)])

(defpartial google-analytics []
  [:script "
  (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
  })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

  ga('create', 'UA-41203760-1', 'techparknews.in');
  ga('send', 'pageview');"])

(defpartial layout [& content]
            (html5
              [:head
               [:title "MTP News"]
               (include-css "/css/style.css")
               (google-analytics)]
              [:body
               [:div#wrapper
                [:header
                 (link-to "/" [:img.logo {:src "/img/hacker-school-logo.png"}])
                 [:h1#logo
                  (link-to "/" "MTP News")]
                 [:ul
                  [:li (link-to "/newest" "new")]
                  [:li (link-to "/newcomments" "comments")]                 
                  [:li (link-to "/leaders" "leaderboard")]
                  #_ [:li (link-to "http://www.hackruiter.com/companies" "jobs")]

                  [:li (link-to "/posts" "my-posts")]
                  [:li (link-to "/submit" "submit")]]
                 
                 (let [hs_id (users/current-user)]
                  (if hs_id
                    [:div.user.loggedin
                      
                      [:span.username (user-link hs_id) " (" (users/get-karma hs_id) ")"]
                      (link-to "/logout" "log out")]
                    [:div.user.loggedout
                     [:ul
                      [:li (link-to "/register" "register")] ;; Uncomment to allow registration
                      [:li (link-to "/login" "log in")]]]))]
                [:div#content content]
                [:footer
                 [:ul
                  [:li (link-to "/lists" "Lists")]
                  [:li (link-to "/bookmarklet" "Bookmarklet")]
                  [:li (link-to "/about" "About")]
                  #_[:li (link-to "http://www.hackerschool.com" "Hacker School")]
                  [:li (link-to "https://github.com/techparknews/hsnews/issues" "Feature Requests")]
                  [:li (link-to "https://github.com/techparknews/hsnews" "Source on Github")]]]]]))

(defpage "/bookmarklet" {}
         (let [bookmarklet-url "javascript:window.location=%22http://www.techparknew.in/submit?link=%22+encodeURIComponent(document.location)+%22&title=%22+encodeURIComponent(document.title)"]
           (layout
             [:h2 "Bookmarklet"]
             [:p "Inspired by the " (link-to "http://ycombinator.com/bookmarklet.html" "Hacker News bookmarklet") " we created one just for Techpark News. When you click on the bookmarklet, it will submit the page you're on. To install, drag this link to your browser toolbar:"]
             (link-to {:class "bookmarkletLink"} bookmarklet-url "post to Techpark News"))))

(defpage "/about" {}
  (layout
   [:ul.listLink
    [:li "We live in a crazy world, tech parks are even crazier island - we come and go almost daily yet doesn't know much about them."
     [:li "There are always questions like"
      [:ul
       [:li "Where will I get the best food in and around this place?"]
       [:li "What are the companies located here and what technologies they used?"]
       [:li "What are the events and conferences happening this week?"]
       [:li "Do they have any walkins going in the neighbourhood?"]
       [:li "or even"]
       [:li "Which is that nice place to sit with your girl friend?"]
       [:li "Which bar servers better, where can I take my team for a drink?"]
       [:li "Where to go for the next team outing etc"]]]]
    [:li "Tech Park News allows us to share info and help each other better understand our working neighbourhood and also to share experiences and information that helps us in work and life"]]))

