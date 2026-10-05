# Xcode loyihasini pullik Apple team'ga moslaydi (ios-release.sh chaqiradi).
# Ishlatish: ruby scripts/ios-setup.rb <TEAM_ID> <VERSION> <BUILD>
require 'xcodeproj'

team, version, build = ARGV
abort 'usage: ios-setup.rb <TEAM_ID> <VERSION> <BUILD>' unless team && version && build

project = Xcodeproj::Project.open(File.expand_path('../ios/App/App.xcodeproj', __dir__))
project.targets.each do |target|
  target.build_configurations.each do |config|
    s = config.build_settings
    s['DEVELOPMENT_TEAM'] = team
    s['CODE_SIGN_STYLE'] = 'Automatic'
    s['MARKETING_VERSION'] = version
    s['CURRENT_PROJECT_VERSION'] = build
    # Sign in with Apple, Push, Universal Links — faqat asosiy app'da (widget'da kerak emas)
    s['CODE_SIGN_ENTITLEMENTS'] = 'App/App.entitlements' if target.name == 'App'
  end
end
project.save
puts "Xcode: team=#{team} version=#{version} build=#{build}"
