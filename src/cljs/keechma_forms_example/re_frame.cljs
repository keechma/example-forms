(ns keechma-forms-example.re-frame
  (:require [forms.re-frame :as f]
            [forms.validator :as v]
            [json-html.core :as j]
            [re-frame.core :as rf]
            [reagent.core :as reagent]))

(def validations
  "Shared validator definitions"
  {:not-empty {:message "can't be empty"
               :validator (fn [v] (not (empty? v)))}
   :valid-email {:message "is not valid email"
                 :validator (fn [v] (not= -1 (.indexOf (or v "") "@")))}
   :long-enough {:message "is too short"
                 :validator (fn [v] (> (count v) 6))}})

(defn to-validator
  "Helper function that extracts the validator definitions."
  [validations config]
  (reduce-kv (fn [m attr v]
               (assoc m attr
                      (map (fn [k] [k (get-in validations [k :validator])]) v))) {} config))

(def validator
  "Defines the validator for the form."
  (v/validator
   (to-validator validations
                 {:username [:not-empty]
                  :name [:not-empty]
                  :email [:valid-email]
                  :password [:long-enough]
                  :accounts.*.username [:not-empty] ;; Validate username for each account
                  :accounts.*.network [:not-empty] ;; Validate network for each account
                  :accounts [:not-empty] ;; Account list must have entries
                  :phone-numbers.* [:not-empty]}))) ;; Validate each phone number in a list

(def form-path [:example-form])

(def form
  "Bind validator to the form"
  (f/constructor validator form-path))

(def inited-form
  "Create a form instance"
  (form {}))

(defn setter
  "Set the value of the key path in the data atom"
  [form-path path]
  (fn [e]
    (rf/dispatch [::f/set! form-path path (.. e -target -value)])))

(defn add-social-media-account
  "Adds a new entry to the `:accounts` attribute. After the data is added it will
  validate the form to potentially remove the validation error for the `:accounts`
  attribute."
  [form-path]
  (fn [e]
    (.preventDefault e)
    (let [form-data-subs (rf/subscribe [::f/data form-path])
          form-data  @form-data-subs
          accounts (or (:accounts form-data) [])]
      (rf/dispatch [::f/set! form-path :accounts (conj accounts {:username nil :network nil})])
      (rf/dispatch [::f/validate! form-path true]))))

(defn add-phone-number
  "Adds a new entry to the `:phone-numbers` attribute. After the data is added it will
  validate the form to potentially remove the validation error for the `:phone-numbers`
  attribute."
  [form-path]
  (fn [e]
    (.preventDefault e)
    (let [form-data-subs (rf/subscribe [::f/data form-path])
          form-data  @form-data-subs
          phone-numbers (or (:phone-numbers form-data) [])]
      (rf/dispatch [::f/set! form-path :phone-numbers (conj phone-numbers nil)])
      (rf/dispatch [::f/validate! form-path true]))))

(defn render-errors
  "Renders the errors for the given key-path."
  [form-path path]
  (let [errors-for-path (rf/subscribe [::f/errors-for-path form-path path])
        errors @errors-for-path]
    (when errors
      [:div.text-danger.errors-wrap
       [:ul.list-unstyled
        (map (fn [error]
               [:li {:key error} (get-in validations [error :message])]) (:failed errors))]])))

(defn render-input
  "Renders an input field and it's errors.

  Validation behaves differently based on the key path error state:

  - If the key path is in the `valid` state, validation will be triggered on blur
  - If the key path is in the `invalid` state, validation will be triggered on change
  "
  ([form-path path label] (render-input form-path path label :text))
  ([form-path path label type]
   (let [form-data-subs (rf/subscribe [::f/data form-path])
         form-data  @form-data-subs
         is-valid? @(rf/subscribe [::f/is-valid-path? form-path path])
         input-setter (setter form-path path)
         on-change-handler (fn [e]
                             (input-setter e)
                             (when (not is-valid?)
                               (rf/dispatch [::f/validate! form-path true])))
         errors @(rf/subscribe [::f/errors-for-path form-path path])]
     [:div.form-group {:class (when (not is-valid?) "has-error")}
      [:label.control-label label]
      [:input.form-control {:type type
                            :value (get-in form-data path)
                            :on-change on-change-handler
                            :on-blur #(rf/dispatch [::f/validate! form-path true])}]
      [render-errors form-path path]])))

(defn render-social-media-select
  "Renders the select box for the social network. After the social network is selected
  it will validate the form to show or remove the error messages."
  [form-path path]
  (let [form-data-subs (rf/subscribe [::f/data form-path])
        form-data  @form-data-subs
        is-valid? @(rf/subscribe [::f/is-valid-path? form-path path])
        select-setter (setter form-path path)
        set-and-validate (fn [e]
                           (select-setter e)
                           (rf/dispatch [::f/validate! form-path true]))]
    [:div.form-group {:class (when (not is-valid?) "has-error")}
     [:label.control-label "Social Network"]
     [:select.form-control {:on-change set-and-validate
                            :value (get-in form-data path)}
      [:option {:value ""} "Please select"]
      [:option {:value "facebook"} "Facebook"]
      [:option {:value "twitter"} "Twitter"]
      [:option {:value "instagram"} "Instagram"]]
     [render-errors form-path path]]))

(defn render-social-media-accounts
  "Renders a list of the social network accounts (nested) form fields."
  [form-path]
  (let [form-data-subs (rf/subscribe [::f/data form-path])
        form-data  @form-data-subs
        accounts (:accounts form-data)]
    (when (pos? (count accounts))
      [:div.well.nested-wrap
       (doall (map-indexed
                (fn [idx item]
                  [:div.social-network-account-wrap {:key idx}
                   [:h3 (str "Account #" (inc idx))]
                   [render-social-media-select form-path [:accounts idx :network]]
                   [render-input form-path [:accounts idx :username] "Username"]]) accounts))])))

(defn render-phone-numbers
  "Renders a list of the phone number input fields"
  [form-path]
  (let [form-data-subs (rf/subscribe [::f/data form-path])
        form-data  @form-data-subs
        phone-numbers (:phone-numbers form-data)]
    [:div.nested-wrap
     (doall (map-indexed
              (fn [idx item]
                [:div {:key idx}
                 [render-input form-path [:phone-numbers idx] (str "Phone number #" (inc idx))]]) phone-numbers))]))

(defn forms-render
  "Main template, renders the form and all of the fields. When the form is submitted
  it will validate the whole form which will potentially render the errors."
  [form-path]
  (let [on-submit (fn [e]
                    (.preventDefault e)
                    (rf/dispatch [::f/validate! form-path]))]
    [:div.container>div.row>div.col-xs-12
     [:form {:on-submit on-submit}
      [:h1 "User Info"]
      [render-input form-path [:username] "Username"]
      [render-input form-path [:password] "Password" :password]
      [render-input form-path [:name] "Name"]
      [render-input form-path [:email] "Email"]
      [:hr]
      [:h2 "Social Network accounts"]
      [:button.btn
       {:on-click (add-social-media-account form-path)}
       "Add Social Network account"]
      [render-social-media-accounts form-path]
      [render-errors form-path :accounts]
      [:hr]
      [:h2 "Phone Numbers"]
      [:button.btn
       {:on-click (add-phone-number form-path)}
       "Add Phone Number"]
      [render-phone-numbers form-path]
      [:hr]
      [:button.btn.btn-primary "Submit"]]
     [:hr]
     [:h1 "Form Data:"]
     [:div.form-data-wrapper
      (j/edn->hiccup @(rf/subscribe [::f/data form-path]))]]))

(defn reload []
  (reagent/render [forms-render form-path]
                  (.getElementById js/document "app")))

(defn ^:export main []
  (reload))
