(ns spid-client-clojure.core
  (:import [no.spid.api.client SpidApiClient$ClientBuilder SpidApiResponse]
           [no.spid.api.oauth SpidOAuthToken]
           [no.spid.api.exceptions SpidApiException SpidOAuthException]
           [no.spid.api.security SpidSecurityHelper])
  (:require [clojure.data.json :as json]
            [clojure.walk :refer [stringify-keys keywordize-keys]]))

(defn- json-parse [data]
  (json/read-str data :key-fn keyword))

(defn- mapify-response [response]
  (let [json (json-parse (.getRawBody response))]
    {:body (.getRawBody response)
     :status (.getResponseCode response)
     :error (:error json)
     :data (:data json)
     :container json
     :success? (<= 200 (.getResponseCode response) 299)}))

(defn- mapify-error [error]
  (let [json (json-parse (.getResponseBody error))]
    {:body (.getResponseBody error)
     :status (.getResponseCode error)
     :error (:error json)
     :container json
     :success? false}))

(def defaults
  {:spid-base-url "https://identity-pre.schibsted.com"
   :redirect-uri "http://localhost:8080"})

(defn create-client [client-id secret & [options]]
  (let [options (merge defaults options)]
    (-> (SpidApiClient$ClientBuilder. client-id
                                      secret
                                      (:signature-secret options)
                                      (:redirect-uri options)
                                      (:spid-base-url options))
        (.build))))

(defn create-server-token [client]
  (.getServerToken client))

(defn create-user-token
  ([client code] (.getUserToken client code))
  ([client username password] (.getUserToken client username password)))

(defmacro request [forms]
  `(try
     (mapify-response (~@forms))
     (catch SpidApiException e#
       (mapify-error e#))
     (catch SpidOAuthException e#
       {:body nil :status nil :error (.getMessage e#) :container nil :success? false})))

(defn GET [client token endpoint & [parameters]]
  (request (.GET client token endpoint (stringify-keys (or parameters {})))))

(defn POST [client token endpoint & [parameters]]
  (request (.POST client token endpoint (stringify-keys parameters))))

(defn PUT [client token endpoint & [parameters]]
  (request (.PUT client token endpoint (stringify-keys parameters))))

(defn DELETE [client token endpoint & [parameters]]
  (request (.DELETE client token endpoint parameters)))

(defn- stringify-map [m]
  (->> m
       stringify-keys
       (map (fn [[k v]] [k (str v)]))
       (into {})))

(defn sign-params [params sign-secret]
  (let [jparams (java.util.HashMap. (stringify-map params))
        security-helper (SpidSecurityHelper. sign-secret)]
    (.addHash security-helper jparams)
    (keywordize-keys (into {} jparams))))
